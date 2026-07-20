package fr.univ_amu.iut.importation.model;

import fr.univ_amu.iut.commun.model.EchelleProgression;
import fr.univ_amu.iut.commun.model.Empreintes;
import fr.univ_amu.iut.commun.model.ExecutionParallele;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.Progression;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.IntStream;

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
/// La copie est **parallèle** (#948) et confiée au socle [ExecutionParallele] (#2039), qui porte les
/// threads virtuels, la borne de concurrence, l'ordre des résultats, la progression monotone et
/// l'annulation coopérative. Cette classe n'écrit plus ce moteur : elle en écrivait une **copie**, à
/// l'identique de celle du découpage.
///
/// Ce qui reste ici est ce qui lui appartient vraiment :
///
/// - **la borne a une raison propre** : chaque copie lit et hache jusqu'à deux SHA-256 (E/S + CPU) ;
///   sans plafond, des centaines de lectures simultanées feraient s'écrouler le débit du support source ;
/// - **l'échelle** : la copie n'est qu'une phase de l'import, sa progression s'arrête donc avant 100 %
///   (cf. [EchelleProgression]) ;
/// - **la reprise #231 et la vérification R9**, par fichier, inchangées ;
/// - chaque copie visant son **nom final R6 unique**, aucune collision d'écriture n'est possible.
///
/// Les événements [SuiviFichiers] arrivent dans le désordre : ciblage par numéro, déjà prévu par le
/// socle (#947).
///
/// Dans les deux cas la sortie (noms des séquences produites en aval) est **identique**, et chaque
/// [SourceOriginal] porte son **numéro de plan** (1..N, ordre des originaux) pour le suivi par fichier.
final class PreparationOriginaux {

    private final CopieProtegee copie;
    private final Renommeur renommeur;
    private final ExecutionParallele moteur;

    PreparationOriginaux(CopieProtegee copie, Renommeur renommeur, int parallelisme) {
        this.copie = Objects.requireNonNull(copie, "copie");
        this.renommeur = Objects.requireNonNull(renommeur, "renommeur");
        this.moteur = new ExecutionParallele(parallelisme);
    }

    /// Copie réalisée (ou retrouvée fidèle, reprise #231) : son nom R6 et son numéro de plan, pour
    /// réassocier la sortie du renommage au suivi par fichier.
    ///
    /// `dejaFidele` n'est pas une donnée de sortie, c'est ce qui permet au libellé de progression de dire
    /// « (déjà présent) » — une mention qui ne se sait qu'**après** examen du fichier, et que le socle
    /// obtient donc via le résultat de la tâche.
    private record CopieRealisee(String nomR6, int numero, boolean dejaFidele) {}

    /// Liste des originaux à transformer (chemin lu + nom logique R6 + numéro de plan). En mode
    /// conservation, copie protégée dans `bruts/` puis renommage R6 (la lecture porte sur ces copies) ;
    /// en mode sans copie, lecture directe de la source avec nom R6 calculé (aucun `bruts/`, aucun
    /// événement de copie sur `suivi`).
    List<SourceOriginal> preparer(
            boolean conserverOriginaux,
            List<Path> originaux,
            Path dossierBruts,
            Prefixe prefixe,
            int totalEtapes,
            Consumer<Progression> progres,
            JetonAnnulation jeton,
            SuiviFichiers suivi) {
        if (!conserverOriginaux) {
            return IntStream.range(0, originaux.size())
                    .mapToObj(i -> new SourceOriginal(
                            originaux.get(i),
                            Renommeur.nomApresRenommage(
                                    originaux.get(i).getFileName().toString(), prefixe),
                            i + 1))
                    .toList();
        }
        Map<String, Integer> numeroParNomR6 =
                copierOriginaux(originaux, dossierBruts, prefixe, totalEtapes, progres, jeton, suivi);
        // Le renommage rescanne `bruts/` : on réassocie chaque copie à son numéro de plan via son nom R6
        // (calculé de façon déterministe à la copie), pour que le suivi par fichier cible la bonne ligne
        // même si l'ordre du scan diffère de celui des originaux.
        return renommeur.renommer(dossierBruts, prefixe).stream()
                .map(chemin -> new SourceOriginal(
                        chemin,
                        chemin.getFileName().toString(),
                        numeroParNomR6.getOrDefault(chemin.getFileName().toString(), 0)))
                .toList();
    }

    /// Copie protégée (R9) **parallèle** (#948) des originaux vers `dossierBruts`, en émettant la
    /// progression « Copie k/N · fichier » (compteur sous verrou, monotone) et les événements de copie
    /// du suivi par fichier (#947). L'annulation (#146) est vérifiée à l'entrée de chaque copie ; une
    /// annulation arrête les copies restantes au lieu d'attendre la fin de toutes celles soumises.
    ///
    /// **Reprise sécurisée (#231)** : un original n'est sauté que si une version renommée existe **et**
    /// que son empreinte SHA-256 est **identique à celle de la source SD** — contenu vérifié, pas
    /// seulement le nom ni la taille. Un fichier absent, périmé ou corrompu (même nom, session orpheline
    /// incohérente) est re-copié : on ne persiste jamais un agrégat sur des fichiers douteux. Sauter une
    /// copie **fidèle** évite au passage le conflit de renommage qu'une re-copie déclencherait.
    ///
    /// @return le numéro de plan (1..N) de chaque copie, indexé par son nom R6 (dans l'ordre du plan)
    private Map<String, Integer> copierOriginaux(
            List<Path> originaux,
            Path dossierBruts,
            Prefixe prefixe,
            int totalEtapes,
            Consumer<Progression> progres,
            JetonAnnulation jeton,
            SuiviFichiers suivi) {
        // `totalEtapes >= 1` est garanti en amont : `ServiceImport` refuse un import sans aucun original
        // (RegleMetierException), et les nuits d'un import multi-nuits viennent d'une partition de
        // fichiers existants. L'échelle le vérifie malgré tout, et c'est voulu : si cette protection
        // disparaissait, mieux vaut un échec net à la construction qu'un import vide passant pour un
        // succès.
        List<CopieRealisee> realisees = moteur.cartographier(
                originaux,
                termine -> "Copie " + termine.faits() + "/" + termine.total() + " · "
                        + termine.element().getFileName()
                        + (termine.resultat().dejaFidele() ? " (déjà présent)" : ""),
                new EchelleProgression(0, totalEtapes),
                (index, original) -> copierUn(original, index + 1, dossierBruts, prefixe, suivi),
                progres,
                jeton);
        // Ordre du plan préservé : le socle rend les résultats dans l'ordre de la liste d'entrée, ce qui
        // est précisément ce dont la réassociation par nom R6 a besoin.
        Map<String, Integer> numeroParNomR6 = new LinkedHashMap<>();
        realisees.forEach(realisee -> numeroParNomR6.put(realisee.nomR6(), realisee.numero()));
        return numeroParNomR6;
    }

    /// Copie un original vers son **nom final R6** (pas d'état intermédiaire au nom d'origine, donc
    /// aucun doublon ni conflit lors du renommage si une version renommée traînait déjà, reprise #231).
    ///
    /// Appelé sur un thread virtuel du socle, qui a déjà acquis le créneau et vérifié l'annulation.
    /// Une copie qui échoue (R9 non fidèle, disque plein) est **fatale**, contrairement aux rejets de la
    /// transformation (#155) : sa `RuntimeException` traverse le socle telle quelle.
    private CopieRealisee copierUn(Path original, int numero, Path dossierBruts, Prefixe prefixe, SuiviFichiers suivi) {
        suivi.copieDemarree(numero);
        Path cible = dossierBruts.resolve(
                Renommeur.nomApresRenommage(original.getFileName().toString(), prefixe));
        boolean dejaFidele =
                Files.isRegularFile(cible) && Empreintes.sha256Hex(cible).equals(Empreintes.sha256Hex(original));
        if (!dejaFidele) {
            copie.copier(original, cible); // écrase une cible corrompue (REPLACE_EXISTING + vérif R9)
        }
        suivi.copieTerminee(numero);
        return new CopieRealisee(cible.getFileName().toString(), numero, dejaFidele);
    }
}
