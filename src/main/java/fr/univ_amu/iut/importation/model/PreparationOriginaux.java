package fr.univ_amu.iut.importation.model;

import fr.univ_amu.iut.commun.model.Empreintes;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.OperationAnnuleeException;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.Progression;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
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
/// La copie est **parallèle** (#948, calquée sur [DecoupageParallele]) : un thread virtuel par fichier,
/// concurrence **bornée** par un [Semaphore] (chaque copie lit + hache jusqu'à deux SHA-256, E/S + CPU ;
/// sans plafond, des centaines de lectures simultanées feraient s'écrouler le débit du support source).
/// Chaque copie visant son **nom final R6 unique**, aucune collision d'écriture n'est possible ; la
/// vérification R9 et la reprise #231 restent par fichier, inchangées. Le résultat est **déterministe**
/// (Future récupérés dans l'ordre de soumission) et la progression (#33) est émise **sous verrou +
/// compteur** (libellés « Copie k/N » monotones). Les événements [SuiviFichiers] arrivent dans le
/// désordre : ciblage par numéro, déjà prévu par le socle (#947).
///
/// Dans les deux cas la sortie (noms des séquences produites en aval) est **identique**, et chaque
/// [SourceOriginal] porte son **numéro de plan** (1..N, ordre des originaux) pour le suivi par fichier.
final class PreparationOriginaux {

    private final CopieProtegee copie;
    private final Renommeur renommeur;
    private final int parallelisme;

    PreparationOriginaux(CopieProtegee copie, Renommeur renommeur, int parallelisme) {
        this.copie = Objects.requireNonNull(copie, "copie");
        this.renommeur = Objects.requireNonNull(renommeur, "renommeur");
        this.parallelisme = parallelisme;
    }

    /// Invariants d'**une** campagne de copie, partagés par tous les threads : cadence globale (compteur
    /// + verrou de progression + sémaphore), callbacks et paramètres de nommage. Objet-paramètre pour
    /// garder [#copierUn] lisible (PMD `ExcessiveParameterList`), miroir de `CampagneDecoupage`.
    private record CampagneCopie(
            Path dossierBruts,
            Prefixe prefixe,
            int nbOriginaux,
            int totalEtapes,
            Consumer<Progression> progres,
            SuiviFichiers suivi,
            JetonAnnulation jeton,
            AtomicInteger copiees,
            Object verrouProgression,
            Semaphore creneaux) {}

    /// Copie réalisée (ou retrouvée fidèle, reprise #231) : son nom R6 et son numéro de plan, pour
    /// réassocier la sortie du renommage au suivi par fichier.
    private record CopieRealisee(String nomR6, int numero) {}

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
        CampagneCopie campagne = new CampagneCopie(
                dossierBruts,
                prefixe,
                originaux.size(),
                totalEtapes,
                progres,
                suivi,
                jeton,
                new AtomicInteger(0),
                new Object(),
                new Semaphore(parallelisme));
        try (ExecutorService executeur = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<CopieRealisee>> copiesEnCours = IntStream.range(0, originaux.size())
                    .mapToObj(i -> executeur.submit(() -> copierUn(originaux.get(i), i + 1, campagne)))
                    .toList();
            try {
                Map<String, Integer> numeroParNomR6 = new LinkedHashMap<>();
                for (Future<CopieRealisee> copieEnCours : copiesEnCours) {
                    CopieRealisee realisee = resultat(copieEnCours);
                    numeroParNomR6.put(realisee.nomR6(), realisee.numero());
                }
                return numeroParNomR6;
            } catch (OperationAnnuleeException annulation) {
                // Annulation (#146) : on arrête les copies restantes au lieu d'attendre la fin de toutes
                // les copies déjà soumises, puis on propage pour que l'appelant nettoie la session.
                copiesEnCours.forEach(copieEnCours -> copieEnCours.cancel(true));
                throw annulation;
            }
        }
    }

    /// Copie un original vers son **nom final R6** (pas d'état intermédiaire au nom d'origine, donc
    /// aucun doublon ni conflit lors du renommage si une version renommée traînait déjà, reprise #231).
    /// Appelé sur un thread virtuel, borné par le sémaphore de la campagne.
    private CopieRealisee copierUn(Path original, int numero, CampagneCopie campagne) throws InterruptedException {
        campagne.creneaux().acquire();
        try {
            campagne.jeton().leverSiAnnule(); // arrêt au plus tôt : les copies pas encore parties s'arrêtent ici
            campagne.suivi().copieDemarree(numero);
            Path cible = campagne.dossierBruts()
                    .resolve(Renommeur.nomApresRenommage(original.getFileName().toString(), campagne.prefixe()));
            boolean dejaFidele =
                    Files.isRegularFile(cible) && Empreintes.sha256Hex(cible).equals(Empreintes.sha256Hex(original));
            if (!dejaFidele) {
                copie.copier(original, cible); // écrase une cible corrompue (REPLACE_EXISTING + vérif R9)
            }
            campagne.suivi().copieTerminee(numero);
            synchronized (campagne.verrouProgression()) {
                int faites = campagne.copiees().incrementAndGet();
                campagne.progres()
                        .accept(new Progression(
                                "Copie " + faites + "/" + campagne.nbOriginaux() + " · " + original.getFileName()
                                        + (dejaFidele ? " (déjà présent)" : ""),
                                (double) faites / campagne.totalEtapes()));
            }
            return new CopieRealisee(cible.getFileName().toString(), numero);
        } finally {
            campagne.creneaux().release();
        }
    }

    /// Récupère le résultat d'une copie lancée sur un thread virtuel (#948). Contrairement aux rejets de
    /// la transformation (#155), une copie qui échoue (R9 non fidèle, disque plein) est **fatale** : la
    /// `RuntimeException` d'origine est relancée telle quelle (annulation #146 comprise).
    private static CopieRealisee resultat(Future<CopieRealisee> copieEnCours) {
        try {
            return copieEnCours.get();
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Copie protégée interrompue.", interruption);
        } catch (ExecutionException echec) {
            if (echec.getCause() instanceof RuntimeException relancable) {
                throw relancable;
            }
            throw new IllegalStateException("Échec de la copie protégée.", echec.getCause());
        }
    }
}
