package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.Progression;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
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

    HydratationDepuisBruts(
            Optional<InventaireBrutsSource> inventaire,
            Optional<RegenerationSequences> regeneration,
            RebranchementSequences rebranchement) {
        this.inventaire = Objects.requireNonNull(inventaire, "inventaire");
        this.regeneration = Objects.requireNonNull(regeneration, "regeneration");
        this.rebranchement = Objects.requireNonNull(rebranchement, "rebranchement");
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
            Consumer<Progression> progres) {
        if (inventaire.isEmpty() || regeneration.isEmpty() || prefixeSession.isEmpty()) {
            return Optional.empty();
        }
        Prefixe prefixe = prefixeSession.orElseThrow();
        return inventaire
                .get()
                .inventorier(dossierSource, prefixe)
                .map(inventorie -> regenererEtRebrancher(sequences, inventorie, prefixe, progres));
    }

    /// Régénère et rebranche, **brut par brut**. Le temporaire est vidé après chaque brut : régénérer une
    /// nuit entière d'un coup doublerait transitoirement l'occupation disque, ce que l'archivage cherchait
    /// justement à éviter. On retient, pour chaque brut ayant produit des séquences, ces séquences : elles
    /// serviront à remplacer le placeholder par les vrais originaux (#1651).
    private ResultatHydratation regenererEtRebrancher(
            List<SequenceDEcoute> sequences,
            InventaireBruts inventorie,
            Prefixe prefixe,
            Consumer<Progression> progres) {
        RegenerationSequences moteur = regeneration.orElseThrow();
        Map<String, SequenceDEcoute> parNom = indexerParNom(sequences);
        Set<String> restantes = new HashSet<>(parNom.keySet());
        BilanReactivation bilan = new BilanReactivation();
        List<BrutRebranche> brutsRebranches = new ArrayList<>();
        List<BrutInventorie> bruts = inventorie.bruts();
        int traites = 0;
        for (BrutInventorie brut : bruts) {
            traites++;
            progres.accept(
                    new Progression("Régénération " + traites + "/" + bruts.size(), traites / (double) bruts.size()));
            Path temporaire = DossierTemporaire.creer("vc-hydrate-");
            try {
                moteur.regenerer(
                        brut.source(), brut.nomOriginal(), prefixe, inventorie.frequenceAcquisitionHz(), temporaire);
                List<SequenceDEcoute> sesSequences = sequencesProduites(temporaire, parNom, restantes);
                if (!sesSequences.isEmpty()) {
                    brutsRebranches.add(new BrutRebranche(brut, sesSequences));
                }
                bilan.absorber(rebranchement.rebrancher(
                        sesSequences, CandidatsReactivation.dans(temporaire), avancement -> {}));
                sesSequences.forEach(sequence -> restantes.remove(sequence.nomFichier()));
            } finally {
                DossierTemporaire.supprimer(temporaire);
            }
        }
        bilan.manquantes += absentesDuDisque(restantes, parNom);
        return new ResultatHydratation(bilan, inventorie.frequenceAcquisitionHz(), brutsRebranches);
    }

    /// Les séquences que **ce brut** a régénérées : celles dont le nom figure parmi les tranches produites,
    /// et qui n'ont pas déjà été rebranchées par un brut précédent. Filtrer ainsi évite de compter comme
    /// « manquantes », à chaque brut, les séquences des autres bruts ; elles ne le sont qu'une fois, à la fin.
    private static List<SequenceDEcoute> sequencesProduites(
            Path temporaire, Map<String, SequenceDEcoute> parNom, Set<String> restantes) {
        try (Stream<Path> produits = Files.walk(temporaire)) {
            return produits.filter(Files::isRegularFile)
                    .map(chemin -> chemin.getFileName().toString())
                    .map(parNom::get)
                    .filter(Objects::nonNull)
                    .filter(sequence -> restantes.contains(sequence.nomFichier()))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture impossible du dossier régénéré " + temporaire, e);
        }
    }

    private static Map<String, SequenceDEcoute> indexerParNom(List<SequenceDEcoute> sequences) {
        Map<String, SequenceDEcoute> parNom = new HashMap<>();
        for (SequenceDEcoute sequence : sequences) {
            parNom.putIfAbsent(sequence.nomFichier(), sequence);
        }
        return parNom;
    }

    private static int absentesDuDisque(Set<String> restantes, Map<String, SequenceDEcoute> parNom) {
        return (int) restantes.stream()
                .map(parNom::get)
                .filter(sequence -> !Files.exists(Path.of(sequence.cheminFichier())))
                .count();
    }
}
