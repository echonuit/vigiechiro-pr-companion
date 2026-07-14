/// Module JavaFX de VigieChiro PR Companion.
///
/// `open module` (et non `module` + `opens fr.univ_amu.iut to javafx.fxml`) : choix volontaire
/// d'ouvrir tout le module à la réflexion. Plus permissif, mais plus simple : FXML et Guice
/// instancient par réflexion sans avoir à déclarer des `opens ... to` ciblés.
open module vigiechiro {
    // JavaFX dependencies (alignées sur les 4 deps + javafx-media du pom).
    requires transitive javafx.base;
    requires transitive javafx.controls;
    requires transitive javafx.graphics;
    requires transitive javafx.fxml;
    requires transitive javafx.media;

    // javafx.swing (SwingFXUtils) + java.desktop (ImageIO / BufferedImage) : capture d'écran
    // hors-écran de l'outil `commun.outils.ApercuFx`.
    requires javafx.swing;
    requires java.desktop;

    // Client HTTP (JDK) pour le pré-remplissage météo Open-Meteo (#547).
    requires java.net.http;

    // Persistance : API JDBC + driver SQLite (module automatique).
    requires java.sql;
    requires org.xerial.sqlitejdbc;

    // Injection de dépendances (Guice 7). `open module` ouvre déjà tous les paquets
    // à la réflexion, donc Guice peut instancier les classes sans `opens ... to`.
    requires com.google.guice;

    // Sérialisation JSON (Gson) des descripteurs de filtres des vues mémorisées (#623). Module
    // automatique ; `open module` ouvre déjà les records du descripteur à sa réflexion.
    requires com.google.gson;

    // Interface en ligne de commande (#614) : picocli parse les sous-commandes/options. `open module`
    // ouvre déjà les paquets à la réflexion picocli (@Command/@Option), donc pas d'`opens ... to`.
    requires info.picocli;

    // Composant audio fourni pour la SAÉ 2.01 (sonogramme / spectrogramme).
    requires fr.nedjar.vigiechiro.audio;

    // Carte interactive des points d'écoute (#152) : Gluon Maps (MapView + MapLayer). Tire les modules
    // Attach storage/util (cache de tuiles). Licence GPL → le dépôt passe en GPL en conséquence.
    requires com.gluonhq.maps;

    // Icônes vectorielles FontAwesome 5 via Ikonli (FontIcon), utilisées par l'écran d'accueil du
    // socle. Le pack `fontawesome5` est requis pour que son IkonProvider soit découvert par le
    // ServiceLoader d'Ikonli dans le graphe de modules (JPMS).
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.fontawesome5;

    // Paquet de base exporté. `open module` ouvre déjà tous les paquets à la réflexion (FXML, Guice),
    // donc les paquets `view` des features n'ont pas besoin d'export explicite.
    exports fr.univ_amu.iut;

    // Auto-découverte des modules de feature (#933) : `RacineInjecteur` charge les `ModuleDeFeature`
    // via `ServiceLoader`. Sur le MODULE-PATH (dev `javafx:run`), c'est ce `provides`/`uses` qui fait
    // foi ; sur le CLASSPATH (tests surefire, fat-jar/Launcher), c'est `META-INF/services`. Les deux
    // listes DOIVENT rester synchronisées (garde-fou : `DecouverteModulesTest` les compare). Le socle
    // (`CommunModule` / `PersistenceModule`) n'est PAS listé : il reste installé explicitement par
    // `RacineInjecteur`.
    uses fr.univ_amu.iut.commun.di.ModuleDeFeature;

    provides fr.univ_amu.iut.commun.di.ModuleDeFeature with
            fr.univ_amu.iut.analyse.di.AnalyseModule,
            fr.univ_amu.iut.audio.di.AudioModule,
            fr.univ_amu.iut.audio.di.ImportVigieChiroModule,
            fr.univ_amu.iut.audio.di.PublicationCorrectionsModule,
            fr.univ_amu.iut.audio.di.DiscussionModule,
            fr.univ_amu.iut.audit.di.AuditModule,
            fr.univ_amu.iut.bibliotheque.di.BibliothequeModule,
            fr.univ_amu.iut.connexion.di.ConnexionModule,
            fr.univ_amu.iut.diagnostic.di.DiagnosticModule,
            fr.univ_amu.iut.importation.di.ImportationModule,
            fr.univ_amu.iut.lot.di.DepotVigieChiroModule,
            fr.univ_amu.iut.lot.di.LotModule,
            fr.univ_amu.iut.multisite.di.MultisiteModule,
            fr.univ_amu.iut.passage.di.PassageModule,
            fr.univ_amu.iut.passage.di.ReconstructionModule,
            fr.univ_amu.iut.passage.di.SynchronisationParticipationModule,
            fr.univ_amu.iut.qualification.di.QualificationModule,
            fr.univ_amu.iut.recherche.di.RechercheModule,
            fr.univ_amu.iut.sites.di.ControleCarreStocModule,
            fr.univ_amu.iut.sites.di.SitesModule,
            fr.univ_amu.iut.sites.di.SynchronisationSitesModule,
            fr.univ_amu.iut.validation.di.ValidationModule;
}
