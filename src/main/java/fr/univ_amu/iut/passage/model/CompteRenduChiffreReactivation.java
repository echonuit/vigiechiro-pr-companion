package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Action;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Avertissement;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Motif;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Segment;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Teinte;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Ventilation;
import fr.univ_amu.iut.passage.model.RapportReactivation.AbsenceReactivation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Traduit une réactivation en **compte rendu chiffré** (#2358), celui que rend
/// [fr.univ_amu.iut.commun.view.PanneauCompteRendu].
///
/// ## Pourquoi il coexiste avec [CompteRenduReactivation]
///
/// Le compte rendu **textuel** ne disparaît pas : la commande `reactiver` le rend en lignes, et un
/// terminal ne dessine pas de barres. Les deux ne se recopient pas pour autant - ils lisent le **même**
/// [RapportReactivation], ne recalculent rien, et **partagent** le titre, le préambule et la conclusion,
/// pour qu'une des deux surfaces ne puisse pas se mettre à dire autre chose que l'autre.
///
/// Ce que le chiffré apporte : la **proportion**. « 4 236 séquence(s) réactivée(s) » ne dit pas si la
/// nuit est exploitable ; la même donnée en barre, à côté des manquantes et des divergentes, le dit d'un
/// coup d'œil.
public final class CompteRenduChiffreReactivation {

    private CompteRenduChiffreReactivation() {}

    /// Le compte rendu chiffré d'une réactivation.
    ///
    /// @param rapport ce que la réactivation a fait
    /// @param actions ce que l'écran propose ensuite ; c'est lui qui sait où mènent ses boutons
    public static CompteRenduChiffre de(RapportReactivation rapport, List<Action> actions) {
        return new CompteRenduChiffre(
                CompteRenduReactivation.titre(rapport),
                resultat(rapport),
                severite(rapport),
                List.of(),
                ventilation(rapport),
                motifs(rapport),
                avertissements(rapport),
                actions);
    }

    /// « 4 236 / 4 236 séquences » : le décompte fait foi, c'est lui qui dit si le passage est écoutable.
    private static String resultat(RapportReactivation rapport) {
        DecompteAudio decompte = rapport.decompte();
        return decompte.presentes() == decompte.total()
                ? decompte.total() + " séquences présentes"
                : decompte.presentes() + " / " + decompte.total() + " séquences";
    }

    /// Complet, c'est un succès ; incomplet, c'est un avertissement et non une erreur : la réactivation a
    /// fait ce qu'elle pouvait, et ce qui manque appelle une action de l'utilisateur, pas un correctif.
    /// Un fichier **divergent** est plus grave : il portait le bon nom sans être le bon audio.
    private static Severite severite(RapportReactivation rapport) {
        if (!rapport.ecarts().isEmpty()) {
            return Severite.ERREUR;
        }
        return rapport.complete() ? Severite.SUCCES : Severite.AVERTISSEMENT;
    }

    /// Ventilation **exhaustive** des séquences attendues : réactivées, déjà présentes, manquantes. Le
    /// total est celui du décompte - ce que le passage devrait avoir - et non la somme des parts, pour
    /// que le reliquat éventuel se voie plutôt que de se dissoudre dans un « autres ».
    ///
    /// Les **divergentes** n'y ont pas de part, et la construction du [Ventilation] l'a montré en refusant
    /// une première version qui leur en donnait une : un fichier divergent n'est pas une quatrième
    /// catégorie de séquence, c'est un fichier refusé **dont la séquence reste manquante**. Lui donner un
    /// segment la comptait deux fois. La divergence est une **cause**, et sa place est dans les motifs.
    private static Ventilation ventilation(RapportReactivation rapport) {
        int total = rapport.decompte().total();
        if (total == 0) {
            return Ventilation.aucune();
        }
        List<Segment> segments = new ArrayList<>();
        ajouterSiPresent(segments, "Réactivées", rapport.reactivees(), Teinte.RETENU);
        ajouterSiPresent(segments, "Déjà présentes", rapport.dejaPresentes(), Teinte.REFERENCE);
        ajouterSiPresent(segments, "Manquantes", rapport.manquantes(), Teinte.ECARTE);
        // Le reliquat porte un nom plutôt que de faire échouer la construction : un décompte qui ne
        // retombe pas juste est un fait à montrer, pas une raison de taire tout le compte rendu.
        long nommees = segments.stream().mapToLong(Segment::quantite).sum();
        ajouterSiPresent(segments, "Non classées", (int) (total - nommees), Teinte.SECONDAIRE);
        return new Ventilation("Devenir des " + total + " séquences du passage", total, segments);
    }

    private static void ajouterSiPresent(List<Segment> segments, String libelle, int quantite, Teinte teinte) {
        if (quantite > 0) {
            segments.add(new Segment(libelle, quantite, String.valueOf(quantite), teinte));
        }
    }

    /// Les motifs, chacun ouvrant sa liste de fichiers : ce qui **manquait** (par motif) et ce qui a été
    /// **refusé** (fichiers portant le bon nom sans être le bon audio).
    ///
    /// Les absences les plus coûteuses d'abord - c'est par elles qu'on commence à chercher, et un brut
    /// absent emporte plusieurs séquences là où une tranche non régénérée n'en emporte qu'une.
    private static List<Motif> motifs(RapportReactivation rapport) {
        List<Motif> motifs = new ArrayList<>(motifsDesAbsences(rapport.absences()));
        if (!rapport.ecarts().isEmpty()) {
            motifs.add(new Motif(
                    "fichier(s) au bon nom mais au mauvais audio, non rebranchés",
                    rapport.ecarts().stream()
                            .map(ecart -> ecart.nomFichier() + " - " + ecart.motif())
                            .toList()));
        }
        return motifs;
    }

    private static List<Motif> motifsDesAbsences(List<AbsenceReactivation> absences) {
        Map<String, List<String>> parMotif = new LinkedHashMap<>();
        absences.stream()
                .sorted(Comparator.comparingInt(AbsenceReactivation::sequences)
                        .reversed()
                        .thenComparing(AbsenceReactivation::nomFichier))
                .forEach(absence -> parMotif.computeIfAbsent(absence.motif(), ignore -> new ArrayList<>())
                        .add(libelleAbsence(absence)));
        return parMotif.entrySet().stream()
                .map(motif -> new Motif("fichier(s) : " + motif.getKey(), motif.getValue()))
                .toList();
    }

    private static String libelleAbsence(AbsenceReactivation absence) {
        return absence.sequences() > 1
                ? absence.nomFichier() + " (" + absence.sequences() + " séquences)"
                : absence.nomFichier();
    }

    /// Ce qui reste vrai et qu'aucune barre ne porte : la voie empruntée, la conclusion sur l'écoutabilité,
    /// l'indice de concordance acoustique, et ce que la phase d'ancrage a rapatrié.
    /// Chaque mention porte son **registre**, et c'est la première capture de cette modale qui l'a exigé :
    /// le composant posait un triangle d'alerte devant « L'audio est de nouveau complet » et devant un
    /// indice annoncé non bloquant. Un compte rendu qui alerte sur une bonne nouvelle apprend à ne plus
    /// regarder ses alertes.
    private static List<Avertissement> avertissements(RapportReactivation rapport) {
        List<Avertissement> avertissements = new ArrayList<>();
        String preambule = CompteRenduReactivation.preambule(rapport);
        if (!preambule.isEmpty()) {
            // La voie empruntée est un fait de contexte : elle explique comment, elle n'alerte sur rien.
            avertissements.add(Avertissement.info(preambule));
        }
        // La conclusion suit l'issue : complet, c'est la bonne nouvelle ; incomplet, c'est ce sur quoi il
        // faudra revenir.
        avertissements.add(
                rapport.complete()
                        ? Avertissement.succes(CompteRenduReactivation.conclusion(rapport))
                        : Avertissement.de(CompteRenduReactivation.conclusion(rapport)));
        IndiceAcoustique indice = rapport.indiceAcoustique();
        if (indice != null && indice.estRenseigne()) {
            avertissements.add(Avertissement.info("Concordance acoustique (indice, non bloquant) : "
                    + indice.concordantes() + " séquence(s) sur " + indice.mesurees()
                    + " présentent les cris attendus."));
        }
        if (rapport.rapatriement() != null && !rapport.rapatriement().texte().isBlank()) {
            avertissements.add(Avertissement.info(rapport.rapatriement().texte()));
        }
        return avertissements;
    }
}
