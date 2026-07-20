package fr.univ_amu.iut.importation.model;

import fr.univ_amu.iut.commun.model.EchelleProgression;
import fr.univ_amu.iut.commun.model.ExecutionParallele;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.Progression;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;

/// Découpe (#12) en **parallèle** la transformation R10/R11 des originaux d'une nuit, extraite de
/// [ServiceImport].
///
/// Le moteur parallèle est celui du socle, [ExecutionParallele] (#2039) : threads virtuels, borne de
/// concurrence, ordre des résultats, progression monotone, annulation coopérative. Cette classe est
/// l'**ancêtre** dont ce socle a été extrait (#1779) ; elle en gardait une copie complète, que la
/// migration supprime.
///
/// Ce qui lui appartient en propre :
///
/// - **la raison de sa borne** : `transformer` charge tout le PCM en mémoire puis écrit sur disque ;
///   sans plafond, une grosse nuit (~1572 fichiers) tiendrait trop de WAV en vol → saturation mémoire ;
/// - **la résilience (#155)** : un original illisible n'abat plus l'import, il devient un
///   [ResultatDecoupage] rejeté **à l'intérieur de la tâche**. C'est là ce qui a rendu la migration
///   possible : le socle n'a jamais d'exception à propager pour ce cas. Une erreur d'écriture workspace
///   reste fatale et le traverse ;
/// - **l'isolation des écritures** dans un temporaire indexé, puis la réconciliation des noms en
///   séquentiel déterministe ;
/// - **l'échelle** : la transformation est la seconde phase de l'import, sa barre reprend là où la copie
///   s'est arrêtée (cf. [EchelleProgression]).
///
/// Le suivi **par fichier** ([SuiviFichiers], #947) est émis hors verrou : chaque événement cible sa
/// ligne par le numéro de plan de la source, l'ordre d'arrivée n'importe pas.
final class DecoupageParallele {

    private final TransformationAudio transformation;
    private final ExecutionParallele moteur;

    DecoupageParallele(TransformationAudio transformation, int parallelisme) {
        this.transformation = Objects.requireNonNull(transformation, "transformation");
        this.moteur = new ExecutionParallele(parallelisme);
    }

    List<ResultatDecoupage> decouper(
            List<SourceOriginal> originaux,
            Path dossierTransformes,
            Prefixe prefixe,
            Integer frequenceAcquisitionLogHz,
            int nbOriginaux,
            int totalEtapes,
            Consumer<Progression> progres,
            JetonAnnulation jeton,
            SuiviFichiers suivi) {
        // Chaque original écrit ses tranches dans un sous-dossier temporaire PROPRE (indexé) : cela évite les
        // écrasements entre écritures parallèles quand deux tranches d'originaux différents visent le même nom
        // horodaté. Les noms définitifs (et la résolution des collisions) sont attribués APRÈS, en séquentiel
        // déterministe, par ReconciliationNoms, qui déplace les fichiers vers `transformes/`.
        Path dossierTemporaire = dossierTransformes.resolve(".tmp-decoupage");

        // Le libellé compte en `nbOriginaux` (les originaux de la NUIT), pas en `originaux.size()` (les
        // sources préparées). L'appelant passe les deux séparément, et rien ne garantit qu'ils coïncident :
        // en mode conservation, les sources viennent d'un rescan de `bruts/`. On ne se sert donc pas de
        // `termine.total()`, qui vaut la taille de la liste traitée.
        //
        // Les étapes précédant la transformation (les copies, en mode conservation) valent
        // `totalEtapes - nbOriginaux` : 0 en mode sans copie. La barre reprend là où la copie s'est arrêtée.
        List<ResultatDecoupage> bruts = moteur.cartographier(
                originaux,
                termine -> "Transformation " + termine.faits() + "/" + nbOriginaux + " · "
                        + termine.element().chemin().getFileName(),
                new EchelleProgression(totalEtapes - nbOriginaux, totalEtapes),
                (index, source) -> decouperUn(
                        source,
                        dossierTemporaire.resolve(Integer.toString(index)),
                        prefixe,
                        frequenceAcquisitionLogHz,
                        suivi),
                progres,
                jeton);

        // Nettoyage sur le seul chemin nominal, volontairement : une annulation traverse `cartographier`
        // sans passer ici, et c'est `MoteurImport` qui supprime alors la session entière. Un `finally`
        // effacerait le temporaire d'une session que l'appelant s'apprête à effacer de toute façon.
        List<ResultatDecoupage> reconcilies = ReconciliationNoms.reconcilier(bruts, dossierTransformes);
        supprimerRecursif(dossierTemporaire);
        return reconcilies;
    }

    /// Supprime récursivement un dossier (nettoyage du temporaire de découpage). Sans échec si absent.
    private static void supprimerRecursif(Path dossier) {
        if (!Files.exists(dossier)) {
            return;
        }
        try (Stream<Path> arbre = Files.walk(dossier)) {
            arbre.sorted(Comparator.reverseOrder()).forEach(chemin -> {
                try {
                    Files.delete(chemin);
                } catch (IOException e) {
                    throw new UncheckedIOException("Nettoyage du temporaire impossible : " + chemin, e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Nettoyage du temporaire impossible : " + dossier, e);
        }
    }

    /// Transforme **un** original, sur un thread virtuel du socle, qui a déjà acquis le créneau et vérifié
    /// l'annulation.
    ///
    /// La **résilience #155 vit ici**, et c'est ce qui rend la migration possible : un original illisible
    /// devient un [ResultatDecoupage] rejeté **à l'intérieur de la tâche**, si bien que le socle n'a jamais
    /// d'exception à propager pour ce cas. Seule une erreur d'écriture workspace
    /// ([UncheckedIOException]) reste fatale et le traverse.
    private ResultatDecoupage decouperUn(
            SourceOriginal source,
            Path dossierSortieOriginal,
            Prefixe prefixe,
            Integer frequenceAcquisitionLogHz,
            SuiviFichiers suivi) {
        suivi.transformationDemarree(source.numero());
        Path original = source.chemin();
        try {
            // Nommage des séquences d'après le nom logique R6 (source.nomR6()), découplé du chemin lu :
            // en mode « sans copie » on lit la source SD mais on nomme comme si elle était renommée R6.
            TransformationOriginal t = transformation.transformer(
                    original, source.nomR6(), dossierSortieOriginal, prefixe, frequenceAcquisitionLogHz);
            suivi.fichierTermine(source.numero());
            return new ResultatDecoupage(original, t, null);
        } catch (OriginalIllisibleException rejet) {
            // Résilience (#155) : une erreur de lecture/format SOURCE est consignée en rejet et l'import
            // continue. Une erreur d'écriture workspace (UncheckedIOException) reste fatale et se propage.
            suivi.fichierRejete(source.numero(), raison(rejet));
            return new ResultatDecoupage(original, null, raison(rejet));
        }
    }

    private static String raison(RuntimeException echec) {
        String message = echec.getMessage();
        return message == null || message.isBlank() ? echec.getClass().getSimpleName() : message;
    }
}
