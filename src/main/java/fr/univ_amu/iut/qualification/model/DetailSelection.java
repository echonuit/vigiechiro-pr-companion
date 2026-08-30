package fr.univ_amu.iut.qualification.model;

import java.util.List;

/// Ce que la jointure de la sélection a pu lire, **et ce qu'elle n'a pas pu** (#4739).
///
/// Une séquence rattachée dont la ligne d'écoute est introuvable disparaissait de la liste sans un
/// mot, si bien que rien ne distinguait « la sélection porte douze séquences » de « elle en porte
/// quinze, dont trois illisibles ». Le désaccord était pourtant détectable : `listening_selection.size`
/// persiste le compte réel.
///
/// C'est le patron de [fr.univ_amu.iut.passage.model.PlanDePaquet], qui nomme la séquence illisible
/// dans ses avertissements plutôt que de la compter pour zéro. Nommer plutôt que refuser : un écran de
/// vérification qui ne s'ouvre plus parce qu'une séquence manque serait un remède plus dur que le mal.
///
/// @param lignes les séquences effectivement jointes, ordonnées par position
/// @param sequencesIntrouvables les identifiants rattachés dont la séquence n'a pas pu être lue
public record DetailSelection(List<SequenceEnSelection> lignes, List<Long> sequencesIntrouvables) {

    public DetailSelection {
        lignes = List.copyOf(lignes);
        sequencesIntrouvables = List.copyOf(sequencesIntrouvables);
    }

    /// `true` quand toutes les séquences rattachées ont été lues.
    public boolean complet() {
        return sequencesIntrouvables.isEmpty();
    }

    /// Ce qui manque, dit en une phrase, ou vide quand rien ne manque.
    ///
    /// La phrase nomme le **compte** et les identifiants : un utilisateur qui voit douze lignes doit
    /// pouvoir savoir que la sélection en portait quinze.
    public String avertissement() {
        if (complet()) {
            return "";
        }
        return sequencesIntrouvables.size() + " séquence(s) de la sélection sont illisibles et ne sont pas"
                + " affichées : " + sequencesIntrouvables;
    }
}
