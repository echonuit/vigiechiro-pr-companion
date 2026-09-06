package fr.univ_amu.iut.commun.model;

/// Le défaut de [PreferenceDesignation] : **le dialogue du système**, sans rien lire.
///
/// Il sert partout où la persistance n'est pas montée - les tests de vue, les outils de capture -
/// et il y rend exactement ce qu'une installation neuve rendrait. Un test de vue n'a donc rien à
/// déclarer pour construire un écran, ce qui était le cas avant ce lot et doit le rester.
///
/// **Ce n'est pas un bouchon.** Un bouchon répond ce qu'on lui dicte ; celui-ci répond le défaut du
/// produit, et c'est `PersistenceModule` qui le remplace là où un réglage peut avoir été écrit.
public final class PreferenceDesignationParDefaut implements PreferenceDesignation {

    @Override
    public boolean dialogueDeLApplication() {
        return ReglageDesignation.DEFAUT;
    }
}
