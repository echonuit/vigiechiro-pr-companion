package fr.univ_amu.iut.passage.viewmodel;

import fr.univ_amu.iut.commun.model.Prefixe;

/// Ce qu'un « Appliquer » ferait au disque : le préfixe **avant**, le préfixe **après**, et la phrase
/// qui les annonce (#1495).
///
/// ## Pourquoi les trois questions ne font qu'un objet
///
/// « Est-ce que ça renomme ? », « combien de séquences ? » et « qu'est-ce que j'affiche ? » se répondent
/// toutes en comparant les deux mêmes préfixes. Les garder séparées dans le ViewModel obligeait à
/// recalculer la paire à trois endroits, et laissait la phrase se construire loin de la comparaison
/// qu'elle décrit.
///
/// @param avant préfixe de session actuel
/// @param apres préfixe de session qu'un « Appliquer » poserait
/// @param nombreSequences séquences de la nuit, à renommer si les deux diffèrent
record ApercuRenommage(Prefixe avant, Prefixe apres, int nombreSequences) {

    /// `true` si appliquer renommerait effectivement sur le disque.
    boolean entraineRenommage() {
        return !avant.nomDossierSession().equals(apres.nomDossierSession());
    }

    /// Combien de séquences seraient renommées, ou **zéro** si rien ne change.
    int sequencesARenommer() {
        return entraineRenommage() ? nombreSequences : 0;
    }

    /// La phrase du récapitulatif. Neutre quand rien ne change : annoncer une action irréversible qui
    /// n'aura pas lieu use l'attention qu'on veut garder pour les cas où elle aura lieu.
    String texte() {
        if (!entraineRenommage()) {
            return "Aucun changement de rattachement.";
        }
        return "Rattachement : "
                + avant.nomDossierSession()
                + " → "
                + apres.nomDossierSession()
                + " : "
                + nombreSequences
                + " séquence(s) de la nuit seront renommées. Action irréversible.";
    }
}
