package fr.univ_amu.iut.importation.model;

import fr.univ_amu.iut.commun.model.Prefixe;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/// Détermine la liste des originaux à transformer selon le choix de **conservation** (#…), en
/// découplant le chemin physiquement lu de son nom logique R6 (cf. [SourceOriginal]). Concern extrait
/// de [ServiceImport] (Extract Class) pour garder l'orchestrateur cohésif : la **politique copie ou
/// non** est une préoccupation autonome, ici collaborant avec [CopieProtegee] (R9) et [Renommeur] (R6).
///
/// - **conservation** : copie protégée dans `bruts/` (R9, reprise #231) puis renommage R6/R7 ; la
///   lecture se fait ensuite sur ces copies (dont le nom est déjà le nom R6) ;
/// - **sans copie** : aucune écriture — les WAV de la source sont lus **en place** (R9, lecture seule),
///   avec leur nom R6 **calculé** ([Renommeur#nomApresRenommage]) ; `bruts/` n'est jamais créé.
///
/// Dans les deux cas la sortie (noms des séquences produites en aval) est **identique**.
final class PreparationOriginaux {

    private final CopieProtegee copie;
    private final Renommeur renommeur;

    PreparationOriginaux(CopieProtegee copie, Renommeur renommeur) {
        this.copie = Objects.requireNonNull(copie, "copie");
        this.renommeur = Objects.requireNonNull(renommeur, "renommeur");
    }

    /// Liste des originaux à transformer (chemin lu + nom logique R6). En mode conservation, copie
    /// protégée dans `bruts/` puis renommage R6 (la lecture porte sur ces copies) ; en mode sans copie,
    /// lecture directe de la source avec nom R6 calculé (aucun `bruts/`).
    List<SourceOriginal> preparer(
            boolean conserverOriginaux,
            List<Path> originaux,
            Path dossierBruts,
            Prefixe prefixe,
            int totalEtapes,
            Consumer<Progression> progres,
            JetonAnnulation jeton) {
        if (!conserverOriginaux) {
            return originaux.stream()
                    .map(source -> new SourceOriginal(
                            source,
                            Renommeur.nomApresRenommage(source.getFileName().toString(), prefixe)))
                    .toList();
        }
        copierOriginaux(originaux, dossierBruts, prefixe, totalEtapes, progres, jeton);
        return renommeur.renommer(dossierBruts, prefixe).stream()
                .map(chemin -> new SourceOriginal(chemin, chemin.getFileName().toString()))
                .toList();
    }

    /// Copie protégée (R9) des originaux vers `dossierBruts`, en émettant la progression « Copie X/N ·
    /// fichier ». Vérifie l'annulation (#146) entre deux fichiers.
    ///
    /// **Reprise sécurisée (#231)** : un original n'est sauté que si une version renommée existe **et**
    /// que son empreinte SHA-256 est **identique à celle de la source SD** — contenu vérifié, pas
    /// seulement le nom ni la taille. Un fichier absent, périmé ou corrompu (même nom, session orpheline
    /// incohérente) est re-copié : on ne persiste jamais un agrégat sur des fichiers douteux. Sauter une
    /// copie **fidèle** évite au passage le conflit de renommage qu'une re-copie déclencherait.
    private void copierOriginaux(
            List<Path> originaux,
            Path dossierBruts,
            Prefixe prefixe,
            int totalEtapes,
            Consumer<Progression> progres,
            JetonAnnulation jeton) {
        int nbOriginaux = originaux.size();
        int indiceCopie = 0;
        for (Path original : originaux) {
            jeton.leverSiAnnule(); // arrêt au plus tôt, entre deux fichiers
            // Copie **directement au nom final** (R6) : pas d'état intermédiaire au nom d'origine, donc
            // aucun doublon ni conflit lors du renommage si une version renommée traînait déjà (reprise).
            Path cible = dossierBruts.resolve(
                    Renommeur.nomApresRenommage(original.getFileName().toString(), prefixe));
            boolean dejaFidele =
                    Files.isRegularFile(cible) && Empreintes.sha256Hex(cible).equals(Empreintes.sha256Hex(original));
            if (!dejaFidele) {
                copie.copier(original, cible); // écrase une cible corrompue (REPLACE_EXISTING + vérif R9)
            }
            indiceCopie++;
            progres.accept(new Progression(
                    "Copie " + indiceCopie + "/" + nbOriginaux + " · " + original.getFileName()
                            + (dejaFidele ? " (déjà présent)" : ""),
                    (double) indiceCopie / totalEtapes));
        }
    }
}
