package fr.univ_amu.iut.bibliotheque.model;

import fr.univ_amu.iut.commun.model.EcrivainCsv;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

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
/// [#exporterVers(Path)] compose les deux : il écrit le CSV puis copie les fichiers de séquences
/// dans un dossier de destination — l'unique effet de bord disque, explicite et déclenché par la
/// couche IHM (jamais à la construction).
///
/// **Déterminisme** (cf. SERVICE-CONVENTIONS §5) : aucun horodatage ni hash dans la sortie,
/// ordre des colonnes et des lignes figé (le service trie les entrées avant de construire
/// l'export) — deux exécutions produisent le même octet, ce qui rend le CSV testable par
/// *approval*.
public record ExportBiblioSons(List<EntreeBiblio> entrees) {

    /// En-tête du CSV récapitulatif. Ordre des colonnes figé (déterminisme).
    public static final List<String> ENTETE =
            List.of("taxon", "sequence source", "fichier", "frequence", "commentaire");

    /// Nom de fichier du CSV récapitulatif écrit par [#exporterVers(Path)] dans le dossier cible.
    public static final String NOM_CSV = "bibliotheque-sons.csv";

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

    /// Matérialise la bibliothèque dans `dossier` : écrit le [CSV récapitulatif][#NOM_CSV] puis
    /// **copie** à côté chaque fichier de séquence existant. Les dossiers parents sont créés au
    /// besoin et une copie déjà présente est écrasée (export idempotent). Une source introuvable sur
    /// disque est **ignorée** (son chemin reste tracé dans le CSV) : l'export reste possible même si
    /// une séquence a été déplacée depuis la validation.
    ///
    /// @param dossier répertoire de destination choisi par l'observateur
    /// @return le nombre de fichiers son effectivement copiés
    /// @throws UncheckedIOException si la création du dossier ou une copie échoue
    public int exporterVers(Path dossier) {
        Objects.requireNonNull(dossier, "dossier");
        try {
            Files.createDirectories(dossier);
            ecrireCsv(dossier.resolve(NOM_CSV));
            int copies = 0;
            for (String chemin : cheminsSequences()) {
                Path source = Path.of(chemin);
                if (Files.isRegularFile(source)) {
                    Files.copy(
                            source,
                            dossier.resolve(source.getFileName().toString()),
                            StandardCopyOption.REPLACE_EXISTING);
                    copies++;
                }
            }
            return copies;
        } catch (IOException echec) {
            throw new UncheckedIOException("Export de la bibliothèque de sons impossible vers " + dossier, echec);
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
