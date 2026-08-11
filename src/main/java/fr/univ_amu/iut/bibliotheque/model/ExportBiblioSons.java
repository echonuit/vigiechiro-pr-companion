package fr.univ_amu.iut.bibliotheque.model;

import fr.univ_amu.iut.commun.model.EcrivainCsv;
import fr.univ_amu.iut.commun.model.EcrivainZip;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.viewmodel.Formats;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/// Bibliothèque de sons de référence exportable (parcours P10, story E8, COULD).
///
/// Produite par [ServiceBibliotheque#exporterBibliotheque()], elle agrège les
/// [entrées][EntreeBiblio] issues des observations marquées « référence ». C'est un **objet de
/// présentation** (pas une entité persistée) : il sait se sérialiser en deux artefacts **sans
/// aucun accès réseau ni effet de bord caché** :
///
/// - un **CSV récapitulatif** (colonnes `taxon`, `sequence source`, `fichier`, `frequence`,
///   `commentaire`) via l'[EcrivainCsv] partagé du socle `commun` ;
/// - la **liste des chemins de fichiers de séquences à copier** (dédupliquée, ordre stable).
///
/// [#exporterVers] compose les deux dans une **archive ZIP** : le CSV à la racine, les sons sous
/// `sons/`, écrits par le socle [EcrivainZip] (#2792) - l'unique effet de bord disque, explicite et
/// déclenché par la couche IHM (jamais à la construction). Une bibliothèque de saison pèse plusieurs
/// centaines de mégaoctets : l'écriture **annonce ce qu'elle emporte, avance entrée par entrée et
/// s'annule**, et un son qui a quitté le disque est **compté** au lieu d'être ignoré en silence
/// (harmonisation avec l'export « observations + sons », cérémonie de l'EPIC #2790).
///
/// **Déterminisme** (cf. SERVICE-CONVENTIONS §5) : aucun horodatage ni hash dans la sortie,
/// ordre des colonnes et des lignes figé (le service trie les entrées avant de construire
/// l'export) : deux exécutions produisent le même octet, ce qui rend le CSV testable par
/// *approval*.
public record ExportBiblioSons(List<EntreeBiblio> entrees) {

    /// En-tête du CSV récapitulatif. Ordre des colonnes figé (déterminisme).
    public static final List<String> ENTETE =
            List.of("taxon", "sequence source", "fichier", "frequence", "commentaire");

    /// Nom du CSV récapitulatif, à la racine de l'archive écrite par [#exporterVers].
    public static final String NOM_CSV = "bibliotheque-sons.csv";

    /// Ce qu'une bibliothèque exportée a réellement emporté : sons copiés, sons dont le fichier a
    /// quitté le disque (nommés, pour que l'observateur sache lesquels manquent), taille de l'archive.
    public record Bilan(int sonsCopies, List<String> sonsIntrouvables, long octets) {

        public Bilan {
            sonsIntrouvables = List.copyOf(sonsIntrouvables);
        }
    }

    /// Copie défensive immuable de la liste d'entrées.
    public ExportBiblioSons {
        entrees = List.copyOf(entrees);
    }

    /// Nombre d'entrées (observations de référence exportées).
    public int nombre() {
        return entrees.size();
    }

    /// Lignes du CSV récapitulatif : l'[en-tête][#ENTETE] suivi d'une ligne par entrée. Les
    /// valeurs `null` (fréquence, commentaire absents) deviennent une chaîne vide.
    public List<List<String>> lignesCsv() {
        List<List<String>> lignes = new ArrayList<>();
        lignes.add(ENTETE);
        for (EntreeBiblio entree : entrees) {
            lignes.add(List.of(
                    texte(entree.taxon()),
                    texte(entree.nomSequence()),
                    texte(entree.cheminFichier()),
                    entree.frequenceKHz() == null ? "" : String.valueOf(entree.frequenceKHz()),
                    texte(entree.commentaire())));
        }
        return lignes;
    }

    /// CSV récapitulatif sérialisé (séparateur `;`, guillemets seulement si nécessaire).
    public String versCsv() {
        return EcrivainCsv.minimal().versChaine(lignesCsv());
    }

    /// Écrit le CSV récapitulatif en UTF-8 dans `fichier` (crée les dossiers parents).
    public void ecrireCsv(Path fichier) {
        EcrivainCsv.minimal().ecrire(fichier, lignesCsv());
    }

    /// Matérialise la bibliothèque dans l'archive `destination` : le [CSV récapitulatif][#NOM_CSV] à la
    /// racine, chaque fichier de séquence existant sous `sons/`. Une source introuvable sur disque est
    /// **comptée et nommée** (son chemin reste tracé dans le CSV) : l'export reste possible même si une
    /// séquence a été déplacée depuis la validation. L'archive partielle ne survit ni à l'échec ni à
    /// l'annulation.
    ///
    /// @param destination archive ZIP choisie par l'observateur
    /// @param surProgression informé de l'annonce puis de chaque entrée écrite
    /// @param jeton annulation coopérative, vérifiée entre deux entrées
    /// @return ce que l'archive a emporté
    /// @throws IOException si l'écriture échoue
    /// @throws fr.univ_amu.iut.commun.model.OperationAnnuleeException si `jeton` est levé
    public Bilan exporterVers(Path destination, Consumer<Progression> surProgression, JetonAnnulation jeton)
            throws IOException {
        Objects.requireNonNull(destination, "destination");
        List<EcrivainZip.EntreeFichier> sons = new ArrayList<>();
        List<String> introuvables = new ArrayList<>();
        for (String chemin : cheminsSequences()) {
            Path source = Path.of(chemin);
            if (Files.isRegularFile(source)) {
                sons.add(new EcrivainZip.EntreeFichier("sons/" + source.getFileName(), source));
            } else {
                introuvables.add(source.getFileName().toString());
            }
        }
        surProgression.accept(annonce(sons));
        long octets = EcrivainZip.ecrire(
                destination, List.of(new EcrivainZip.EntreeTexte(NOM_CSV, versCsv())), sons, surProgression, jeton);
        return new Bilan(sons.size(), List.copyOf(introuvables), octets);
    }

    /// L'annonce qui ouvre la modale : ce que l'archive va contenir, volume compris.
    private Progression annonce(List<EcrivainZip.EntreeFichier> sons) {
        long octets = sons.stream().mapToLong(son -> taille(son.source())).sum();
        return new Progression(
                nombre() + " référence(s) · " + sons.size() + " son(s) · ~" + Formats.octetsLisibles(octets), 0.0);
    }

    /// La taille du fichier, ou `0` s'il est parti entre la vérification et l'annonce : le volume
    /// annoncé est un ordre de grandeur, et l'écriture signalera la disparition mieux que l'annonce.
    private static long taille(Path source) {
        try {
            return Files.size(source);
        } catch (IOException disparu) {
            return 0L;
        }
    }

    /// Chemins des fichiers de séquences à copier, **dédupliqués** (une séquence portant plusieurs
    /// observations de référence n'est copiée qu'une fois) et dans l'ordre des entrées.
    public List<String> cheminsSequences() {
        LinkedHashSet<String> chemins = new LinkedHashSet<>();
        for (EntreeBiblio entree : entrees) {
            if (entree.cheminFichier() != null) {
                chemins.add(entree.cheminFichier());
            }
        }
        return List.copyOf(chemins);
    }

    private static String texte(String valeur) {
        return valeur == null ? "" : valeur;
    }
}
