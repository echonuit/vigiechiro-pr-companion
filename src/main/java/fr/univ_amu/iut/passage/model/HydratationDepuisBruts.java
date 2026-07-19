package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.model.ExecutionParallele;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.NommageSequences;
import fr.univ_amu.iut.commun.model.NommageSequences.TranchesAttendues;
import fr.univ_amu.iut.commun.model.Nuit;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.passage.model.RebranchementSequences.OrigineCandidats;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/// La voie **« bruts »** d'un passage **reconstruit** (#1650, EPIC #1653) : l'hydratation.
///
/// Un passage reconstruit (#1305) ne porte pas l'inventaire de ses originaux, seulement le nom de ses
/// séquences (issu du CSV distant) et un placeholder. La voie « bruts » ordinaire
/// ([ReactivationDepuisBruts]) ne s'applique donc pas : elle part des **originaux connus**. Ici on part
/// **du dossier** : on l'inventorie ([InventaireBrutsSource], qui lit la fréquence d'acquisition du log),
/// on régénère chaque brut à cette fréquence, et on rebranche les tranches produites **par nom** sur les
/// séquences existantes, via **la même garde** ([RebranchementSequences]) que n'importe quel candidat.
///
/// **Aucune preuve d'identité du brut** n'est possible (le passage n'a jamais capturé d'empreinte
/// d'original) : c'est la **cascade** au niveau des tranches (#1309, structure + cris attendus) qui fait
/// foi. Un brut d'une autre nuit produit des tranches d'autres noms, qui ne se rebranchent sur rien ; un
/// brut du bon nom mais du mauvais contenu produit des tranches que la cascade refuse.
///
/// Hydrater n'est possible que si la feature « Importation » est active (inventaire **et** régénération)
/// et si le dossier porte un log exploitable. Sinon, [#appliquer] renonce ([Optional#empty]) et la
/// réactivation reste sur le compte rendu honnête (#1648).
final class HydratationDepuisBruts {

    private final Optional<InventaireBrutsSource> inventaire;
    private final Optional<RegenerationSequences> regeneration;
    private final RebranchementSequences rebranchement;

    /// Régénère les bruts **en parallèle** (#1779) : la transformation audio d'un brut est la partie
    /// coûteuse, et une nuit en compte souvent des dizaines. Concurrence bornée, progression thread-safe,
    /// jeton honoré : cf. [ExecutionParallele].
    private final ExecutionParallele executionParallele;

    HydratationDepuisBruts(
            Optional<InventaireBrutsSource> inventaire,
            Optional<RegenerationSequences> regeneration,
            RebranchementSequences rebranchement,
            ExecutionParallele executionParallele) {
        this.inventaire = Objects.requireNonNull(inventaire, "inventaire");
        this.regeneration = Objects.requireNonNull(regeneration, "regeneration");
        this.rebranchement = Objects.requireNonNull(rebranchement, "rebranchement");
        this.executionParallele = Objects.requireNonNull(executionParallele, "executionParallele");
    }

    /// Tente d'hydrater le passage depuis `dossierSource`.
    ///
    /// Rend le bilan si l'hydratation a pu s'exécuter (feature « Importation » active, préfixe connu, log
    /// exploitable dans le dossier), `Optional.empty()` sinon : la réactivation reste alors sur le compte
    /// rendu honnête du palier 1 (#1648), sans rien inventer.
    Optional<ResultatHydratation> appliquer(
            List<SequenceDEcoute> sequences,
            Path dossierSource,
            Optional<Prefixe> prefixeSession,
            Consumer<Progression> progres,
            JetonAnnulation jeton) {
        if (inventaire.isEmpty() || regeneration.isEmpty() || prefixeSession.isEmpty()) {
            return Optional.empty();
        }
        Prefixe prefixe = prefixeSession.orElseThrow();
        return inventaire
                .get()
                .inventorier(dossierSource, prefixe)
                .map(inventorie -> regenererEtRebrancher(sequences, inventorie, prefixe, progres, jeton));
    }

    /// Régénère et rebranche, **brut par brut mais en parallèle** (#1779) : la transformation audio d'un brut
    /// est la partie coûteuse (une nuit en compte des dizaines), et la faire séquentiellement rendait la
    /// réactivation très longue. Chaque brut est régénéré dans **son** temporaire, vidé aussitôt après son
    /// rebranchement : régénérer une nuit entière d'un coup doublerait l'occupation disque, ce que l'archivage
    /// cherchait à éviter - le [ExecutionParallele] borne donc le nombre de temporaires vivants à la fois.
    ///
    /// On retient, pour chaque brut ayant produit des séquences, ces séquences (elles serviront à remplacer le
    /// placeholder par les vrais originaux, #1651), et on **revendique** chaque séquence de façon atomique :
    /// un seul brut la rebranche, même si deux bruts en produisent une de même nom. Les contributions sont
    /// **réduites dans l'ordre de la liste des bruts**, pour un bilan et une adoption déterministes.
    private ResultatHydratation regenererEtRebrancher(
            List<SequenceDEcoute> sequences,
            InventaireBruts inventorie,
            Prefixe prefixe,
            Consumer<Progression> progres,
            JetonAnnulation jeton) {
        Map<String, SequenceDEcoute> parNom = indexerParNom(sequences);
        Set<String> restantes = ConcurrentHashMap.newKeySet();
        restantes.addAll(parNom.keySet());
        List<BrutInventorie> bruts = nuit(sequences, inventorie.bruts());

        // L'arbitrage des collisions de noms, rejoué comme l'import le fait (#1934). Sans lui, chaque brut
        // régénéré seul se nomme toujours en `_000`, et la tranche que l'import avait renommée `_001` pour
        // cause de collision n'est revendiquée par personne - puis son brut, n'ayant rien revendiqué, n'est
        // pas adopté et disparaît de la base. Le calcul est pur : il ne coûte que des chaînes, et laisse la
        // régénération parallèle et brut par brut.
        Map<String, List<String>> nomsArbitres = arbitrer(bruts, inventorie, prefixe);

        List<ContributionBrut> contributions = executionParallele.cartographier(
                bruts,
                "Régénération",
                brut -> regenererUn(brut, inventorie, prefixe, parNom, restantes, nomsArbitres),
                progres,
                jeton);

        BilanReactivation bilan = new BilanReactivation();
        List<BrutRebranche> brutsRebranches = new ArrayList<>();
        for (ContributionBrut contribution : contributions) {
            bilan.absorber(contribution.bilan());
            contribution.rebranche().ifPresent(brutsRebranches::add);
        }
        bilan.manquantes += absentesDuDisque(restantes, parNom);
        return new ResultatHydratation(bilan, inventorie.frequenceAcquisitionHz(), brutsRebranches);
    }

    /// Rejoue l'arbitrage des collisions sur la nuit, **exactement** comme l'import (`ReconciliationNoms`),
    /// à partir des durées lues dans les en-têtes (#1934).
    ///
    /// Rend une table **vide** dès qu'un seul brut n'a pas livré sa durée : arbitrer sur un inventaire
    /// incomplet décalerait les noms de tous les bruts suivants, ce qui produirait des tranches nommées
    /// comme l'import ne les a jamais écrites. Ne pas arbitrer laisse quelques tranches non revendiquées ;
    /// arbitrer faux en ferait rebrancher de mauvaises. On préfère le trou au mensonge.
    ///
    /// L'arbitrage porte ici sur les bruts **présents dans le dossier**, là où la voie « bruts » (#1932)
    /// s'appuie sur les originaux connus en base : un passage reconstruit n'en a aucun. Quand l'utilisateur
    /// redonne la carte entière, les deux retombent sur la liste que l'import avait écrite.
    private static Map<String, List<String>> arbitrer(
            List<BrutInventorie> bruts, InventaireBruts inventorie, Prefixe prefixe) {
        if (bruts.stream().anyMatch(brut -> brut.nombreTrames() == null)) {
            return Map.of();
        }
        List<TranchesAttendues> attendues = bruts.stream()
                .map(brut -> new TranchesAttendues(
                        brut.nomOriginal(),
                        NommageSequences.nombreTranches(
                                brut.nombreTrames() / (double) inventorie.frequenceAcquisitionHz())))
                .toList();
        return NommageSequences.arbitrer(prefixe, attendues);
    }

    /// Régénère **un** brut dans son propre temporaire et rebranche les séquences qu'il revendique. Rend sa
    /// contribution (bilan partiel + brut rebranché éventuel) ; le temporaire est supprimé dans tous les cas.
    /// Exécuté sur un thread de [ExecutionParallele] : ne touche que du local et le `Set` concurrent
    /// `restantes` (revendication atomique), jamais le bilan agrégé (réduit en aval, en séquentiel).
    private ContributionBrut regenererUn(
            BrutInventorie brut,
            InventaireBruts inventorie,
            Prefixe prefixe,
            Map<String, SequenceDEcoute> parNom,
            Set<String> restantes,
            Map<String, List<String>> nomsArbitres) {
        RegenerationSequences moteur = regeneration.orElseThrow();
        Path temporaire = DossierTemporaire.creer("vc-hydrate-");
        try {
            // Aucun arbitrage de collisions ici, faute de pouvoir le rejouer : l'hydratation part d'un
            // passage reconstruit, dont la base ne connaît aucun original réel (ni durée, donc ni nombre
            // de tranches). L'inventaire des bruts ne porte pas non plus les durées. Une tranche ayant
            // perdu une collision à l'import reste donc non revendiquée sur ce chemin.
            SequencesRegenerees regenerees = moteur.regenerer(
                    brut.source(),
                    brut.nomOriginal(),
                    prefixe,
                    inventorie.frequenceAcquisitionHz(),
                    temporaire,
                    nomsArbitres.getOrDefault(brut.nomOriginal(), List.of()));
            List<SequenceDEcoute> sesSequences = sequencesRevendiquees(temporaire, parNom, restantes);
            Optional<BrutRebranche> rebranche = sesSequences.isEmpty()
                    ? Optional.empty()
                    : Optional.of(new BrutRebranche(brut, sesSequences, regenerees.empreinteSource()));
            BilanReactivation bilan = rebranchement.rebrancher(
                    sesSequences,
                    CandidatsReactivation.dans(temporaire),
                    OrigineCandidats.REGENERATION,
                    avancement -> {});
            return new ContributionBrut(bilan, rebranche);
        } finally {
            DossierTemporaire.supprimer(temporaire);
        }
    }

    /// La contribution d'**un** brut à l'hydratation : son bilan partiel (à absorber dans le bilan global) et,
    /// s'il a produit des séquences, le brut rebranché (pour l'adoption des originaux, #1651).
    private record ContributionBrut(BilanReactivation bilan, Optional<BrutRebranche> rebranche) {}

    /// Les séquences que **ce brut** régénère et **revendique** : celles dont le nom figure parmi les tranches
    /// produites et qu'aucun autre brut n'a déjà prises. La revendication est **atomique**
    /// ([Set#remove] sur un `Set` concurrent) : sur deux bruts produisant une séquence de même nom, un seul la
    /// rebranche. Les séquences jamais revendiquées par aucun brut sont comptées « manquantes » une seule fois,
    /// à la fin ([#absentesDuDisque]).
    private static List<SequenceDEcoute> sequencesRevendiquees(
            Path temporaire, Map<String, SequenceDEcoute> parNom, Set<String> restantes) {
        List<SequenceDEcoute> produites;
        try (Stream<Path> fichiers = Files.walk(temporaire)) {
            produites = fichiers.filter(Files::isRegularFile)
                    .map(chemin -> parNom.get(chemin.getFileName().toString()))
                    .filter(Objects::nonNull)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture impossible du dossier régénéré " + temporaire, e);
        }
        List<SequenceDEcoute> revendiquees = new ArrayList<>();
        for (SequenceDEcoute sequence : produites) {
            if (restantes.remove(sequence.nomFichier())) {
                revendiquees.add(sequence);
            }
        }
        return revendiquees;
    }

    private static Map<String, SequenceDEcoute> indexerParNom(List<SequenceDEcoute> sequences) {
        Map<String, SequenceDEcoute> parNom = new HashMap<>();
        for (SequenceDEcoute sequence : sequences) {
            parNom.putIfAbsent(sequence.nomFichier(), sequence);
        }
        return parNom;
    }

    /// Ne garde que les bruts de **la ou les nuits du passage** (#1724). Sur une carte SD **multi-nuits**,
    /// régénérer les bruts des nuits voisines est du travail pur perdu : leurs tranches ne se
    /// rebrancheraient sur aucune séquence (autres horodatages). On les écarte donc **avant** la
    /// transformation, qui est la partie coûteuse - le résultat était déjà correct, seul le temps change.
    ///
    /// Prudence : on n'écarte un brut que si l'on est **sûr** qu'il est d'une autre nuit. S'il ne porte pas
    /// d'horodatage exploitable, ou si le passage n'a aucune nuit déductible (fixtures aux noms non
    /// standard), on le **garde** - le nommage des tranches fera de toute façon foi (#1650), on ne troque
    /// jamais l'efficience contre une régression.
    private static List<BrutInventorie> nuit(List<SequenceDEcoute> sequences, List<BrutInventorie> bruts) {
        Set<LocalDate> nuitsPassage = sequences.stream()
                .map(sequence -> Prefixe.horodatageDe(sequence.nomFichier()))
                .flatMap(Optional::stream)
                .map(Nuit::de)
                .collect(Collectors.toSet());
        if (nuitsPassage.isEmpty()) {
            return bruts;
        }
        return bruts.stream()
                .filter(brut -> Prefixe.horodatageDe(brut.nomOriginal())
                        .map(Nuit::de)
                        .map(nuitsPassage::contains)
                        .orElse(true))
                .toList();
    }

    private static int absentesDuDisque(Set<String> restantes, Map<String, SequenceDEcoute> parNom) {
        return (int) restantes.stream()
                .map(parNom::get)
                .filter(sequence -> !sequence.estSurLeDisque())
                .count();
    }
}
