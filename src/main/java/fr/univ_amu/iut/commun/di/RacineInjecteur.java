package fr.univ_amu.iut.commun.di;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import fr.univ_amu.iut.analyse.di.AnalyseModule;
import fr.univ_amu.iut.audio.di.AudioModule;
import fr.univ_amu.iut.audio.di.ImportVigieChiroModule;
import fr.univ_amu.iut.bibliotheque.di.BibliothequeModule;
import fr.univ_amu.iut.connexion.di.ConnexionModule;
import fr.univ_amu.iut.diagnostic.di.DiagnosticModule;
import fr.univ_amu.iut.importation.di.ImportationModule;
import fr.univ_amu.iut.lot.di.DepotVigieChiroModule;
import fr.univ_amu.iut.lot.di.LotModule;
import fr.univ_amu.iut.multisite.di.MultisiteModule;
import fr.univ_amu.iut.passage.di.PassageModule;
import fr.univ_amu.iut.qualification.di.QualificationModule;
import fr.univ_amu.iut.recherche.di.RechercheModule;
import fr.univ_amu.iut.sites.di.SitesModule;
import fr.univ_amu.iut.validation.di.ValidationModule;
import java.util.List;

/// Racine de composition Guice de l'application (composition root).
///
/// C'est le seul endroit qui connaît la liste des modules à assembler : le socle ([CommunModule] +
/// [PersistenceModule]) et l'ensemble des features (sites, passage,
/// qualification, validation, multisite, importation, lot, diagnostic, bibliotheque). Chaque
/// feature
/// publie ses DAO et ses services via son propre module Guice ; cette racine se contente de les
/// installer. La feature `cli` ne s'installe pas ici : c'est elle qui crée l'injecteur enfant
/// (`RacineInjecteur.creer().createChildInjector(new CliModule())`).
///
/// Note d'architecture : ce paquet `commun.di` dépend des features (il les assemble), ce
/// qui est normal pour une racine de composition. Le test `ArchitectureTest` ignore donc
/// explicitement les dépendances issues de `commun.di` dans la détection de cycles.
public final class RacineInjecteur {

    private RacineInjecteur() {}

    /// Crée l'injecteur applicatif avec tous les modules câblés.
    public static Injector creer() {
        return Guice.createInjector(modules());
    }

    /// Liste des modules applicatifs (socle + toutes les features), **source unique** de la
    /// composition. Exposée pour permettre des **overrides ciblés** sans la dupliquer :
    /// `Modules.override(RacineInjecteur.modules()).with(...)`, utilisé par les outils de capture qui
    /// rendent le **chrome complet** (`MainView`/`MainController`, qui dépend de toutes les features)
    /// avec une horloge figée ou un service no-op.
    public static List<Module> modules() {
        return List.of(
                new CommunModule(),
                new PersistenceModule(),
                new SitesModule(),
                new PassageModule(),
                new QualificationModule(),
                new ValidationModule(),
                new MultisiteModule(),
                new ImportationModule(),
                new LotModule(),
                new DepotVigieChiroModule(),
                new DiagnosticModule(),
                new BibliothequeModule(),
                new RechercheModule(),
                new AnalyseModule(),
                new AudioModule(),
                new ImportVigieChiroModule(),
                new ConnexionModule());
    }
}
