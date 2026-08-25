package fr.univ_amu.iut.multisite.viewmodel;

import fr.univ_amu.iut.commun.model.IssueTraitement;
import fr.univ_amu.iut.commun.model.ResultatTraitementGroupe;
import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Action;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Avertissement;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Motif;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Segment;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Teinte;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Ventilation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Traduit le résultat d'un **traitement en lot** en compte rendu chiffré (#2757).
///
/// Un compte rendu chiffré dit trois choses qu'une phrase perd : la **proportion**, sur vingt nuits
/// « 14 réussi(s) » demandant de soustraire ; le **reliquat**, où « non traité » agrège un écarté
/// (« dépôt déjà préparé », bénin) et un échec (« la plateforme a refusé », à reprendre) qui
/// n'appellent pas la même conduite ; et les **motifs**, une ligne par nuit là où trois nuits
/// partagent le même.
///
/// [IssueTraitement.Statut] les énumère, et elles couvrent le tout : c'est la forme que le constructeur de
/// [Ventilation] sait exiger. Les teintes suivent la **conduite attendue** et non la mécanique : un écarté
/// est [Teinte#ECARTE] parce qu'il n'y a rien à faire, un échec est [Teinte#REFUSE] parce qu'il faut y
/// revenir.
///
/// Les motifs regroupent leurs sujets : « écarté : dépôt déjà préparé » cité une fois, avec les trois
/// nuits concernées, plutôt que trois fois la même phrase. Le préfixe de statut est conservé, sans quoi un
/// motif d'écart et un motif d'échec se liraient pareil dans la même liste.
///
/// ## Pourquoi quatre parts, et non trois
///
/// Le statut [IssueTraitement.Statut#NON_TRAITE] n'apparaît que sur un lot **arrêté** : ces passages n'ont
/// jamais été atteints. Il se laisse donc oublier, et l'oubli ne se voit pas sur un lot qui va au bout.
///
/// Il porte [Teinte#REFERENCE], seule teinte **sans jugement** : rien n'a été tenté, donc ni réussite, ni
/// écart décidé, ni échec. La distinguer d'« Écartés » compte, parce que les deux appellent des conduites
/// opposées - un écarté était déjà fait, un non traité attend qu'on relance.
public final class CompteRenduChiffreLot {

    private CompteRenduChiffreLot() {}

    /// Le compte rendu chiffré d'un lot terminé.
    ///
    /// @param resultat les issues du lot, dans l'ordre de soumission, et le fait qu'il ait été interrompu
    /// @param actions ce que l'écran propose ensuite ; c'est lui qui sait où mènent ses boutons
    public static CompteRenduChiffre de(ResultatTraitementGroupe resultat, List<Action> actions) {
        return new CompteRenduChiffre(
                titre(resultat),
                resultatLisible(resultat),
                severite(resultat),
                List.of(),
                ventilation(resultat),
                motifs(resultat),
                avertissements(resultat),
                actions);
    }

    /// Le libellé de l'action, et le fait qu'on l'ait arrêtée : c'est la première chose à savoir.
    private static String titre(ResultatTraitementGroupe resultat) {
        return resultat.interrompu() ? resultat.libelleAction() + " - interrompu" : resultat.libelleAction();
    }

    /// « 20 traités », ou « 14 / 20 traités » dès qu'il en manque.
    private static String resultatLisible(ResultatTraitementGroupe resultat) {
        long total = resultat.issues().size();
        return resultat.reussis() == total ? total + " traités" : resultat.reussis() + " / " + total + " traités";
    }

    /// Un échec appelle une reprise, donc un avertissement. Un écart, non : c'était déjà fait.
    private static Severite severite(ResultatTraitementGroupe resultat) {
        if (compter(resultat, IssueTraitement.Statut.ECHEC) > 0 || resultat.interrompu()) {
            return Severite.AVERTISSEMENT;
        }
        return resultat.reussis() == resultat.issues().size() ? Severite.SUCCES : Severite.INFO;
    }

    /// Les trois statuts, dans l'ordre où on veut les lire, et seulement ceux qui ont eu lieu.
    private static Ventilation ventilation(ResultatTraitementGroupe resultat) {
        List<Segment> segments = new ArrayList<>();
        ajouter(segments, resultat, IssueTraitement.Statut.REUSSI, "Traités", Teinte.RETENU);
        ajouter(segments, resultat, IssueTraitement.Statut.ECARTE, "Écartés", Teinte.ECARTE);
        ajouter(segments, resultat, IssueTraitement.Statut.ECHEC, "En échec", Teinte.REFUSE);
        // Le QUATRIÈME statut, qu'on oublie parce qu'il n'existe que sur un lot arrêté : jamais atteint.
        // Sans lui la ventilation n'est pas exhaustive, et son constructeur la refuse - c'est ainsi que
        // l'oubli s'est vu, plutôt qu'en produisant une barre qui mentait de la part manquante.
        ajouter(segments, resultat, IssueTraitement.Statut.NON_TRAITE, "Non traités", Teinte.REFERENCE);

        return new Ventilation("passages soumis", resultat.issues().size(), segments);
    }

    private static void ajouter(
            List<Segment> segments,
            ResultatTraitementGroupe resultat,
            IssueTraitement.Statut statut,
            String libelle,
            Teinte teinte) {
        long combien = compter(resultat, statut);
        if (combien > 0) {
            segments.add(new Segment(libelle, combien, combien + " passage(s)", teinte));
        }
    }

    /// Un motif, ses sujets. Le regroupement préserve l'ordre de première apparition : c'est celui de la
    /// sélection, donc celui que l'utilisateur a sous les yeux.
    private static List<Motif> motifs(ResultatTraitementGroupe resultat) {
        Map<String, List<String>> parMotif = new LinkedHashMap<>();
        for (IssueTraitement issue : resultat.issues()) {
            if (issue.statut() != IssueTraitement.Statut.REUSSI) {
                parMotif.computeIfAbsent(qualifier(issue), motif -> new ArrayList<>())
                        .add(issue.cible().designation());
            }
        }
        return parMotif.entrySet().stream()
                .map(entree -> new Motif(entree.getKey(), entree.getValue()))
                .toList();
    }

    /// Le motif, préfixé de ce qui est arrivé : les mêmes mots que la version textuelle employait, pour
    /// qu'un écart et un échec ne se confondent pas dans une liste qui les mêle.
    private static String qualifier(IssueTraitement issue) {
        return switch (issue.statut()) {
            case ECARTE -> "écarté : " + issue.motif();
            case ECHEC -> "échec : " + issue.motif();
            case NON_TRAITE -> "non traité : le lot a été arrêté avant";
            case REUSSI -> issue.motif();
        };
    }

    /// Ce que la barre ne peut pas dire : qu'une relance reprendra ce qui reste, et rien d'autre.
    private static List<Avertissement> avertissements(ResultatTraitementGroupe resultat) {
        if (!resultat.interrompu()) {
            return List.of();
        }
        long restants = resultat.issues().size() - resultat.reussis();
        return List.of(Avertissement.de("Vous avez arrêté le lot. Relancer la même action ne reprendra que les "
                + restants + " passage(s) restants."));
    }

    private static long compter(ResultatTraitementGroupe resultat, IssueTraitement.Statut statut) {
        return resultat.issues().stream()
                .filter(issue -> issue.statut() == statut)
                .count();
    }
}
