package fr.univ_amu.iut.commun.model;

import com.google.inject.ImplementedBy;

/// Quel dispositif l'utilisateur veut pour **désigner un fichier** : celui du système, ou celui de
/// l'application.
///
/// ## Pourquoi un port, et non `Reglages` directement
///
/// La première version de ce lot donnait `Reglages` à la fabrique de sélecteurs. Elle compilait, ses
/// tests passaient, et elle a rendu **293 tests rouges** dans 27 classes : `Reglages` est fourni par
/// `PersistenceModule`, si bien que construire un écran exigeait désormais une base de données. Les
/// tests de vue bâtissent un injecteur minimal, avec des mocks et un `@Provides` par collaborateur -
/// aucun n'avait de persistance, et aucun n'en avait besoin.
///
/// Le couplage ne se voyait ni à la compilation ni à la lecture : la fabrique recevait *un*
/// collaborateur, ce qui paraît anodin, mais ce collaborateur traîne une table SQL derrière lui.
///
/// Ce port porte **la seule chose que la vue a besoin de savoir**, et son défaut `@ImplementedBy`
/// est constructible sans rien. C'est le patron de [DepotDispositionColonnes] : un défaut en
/// mémoire, que la persistance remplace là où elle existe.
@ImplementedBy(PreferenceDesignationParDefaut.class)
public interface PreferenceDesignation {

    /// L'utilisateur a-t-il demandé le dialogue de l'application ?
    ///
    /// `false` vaut « celui du système », qui est le défaut : une installation qui n'a jamais touché
    /// ce réglage se comporte comme avant.
    boolean dialogueDeLApplication();
}
