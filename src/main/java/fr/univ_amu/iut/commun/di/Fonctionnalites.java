package fr.univ_amu.iut.commun.di;

import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.ReglagesDao;
import fr.univ_amu.iut.commun.persistence.DataAccessException;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/// Registre des **feature-flags** (#1057) : décide, à la composition (avant l'injecteur Guice),
/// quelles features installer. L'état actif de chaque feature est résolu par **précédence** :
///
/// 1. propriété système `-Dvigiechiro.features.<id>=on|off` (override CI/dev, la plus forte) ;
/// 2. alias rétro-compatible `-Dvigiechiro.features.desactivees=<NomClasseSimple>,...` ;
/// 3. flag persisté `feature.<id>.active` dans `app_setting` (posé par l'écran Réglages), lu en
///    **pré-bootstrap** (hors Guice) et **tolérant** (base ou table absente = aucun flag) ;
/// 4. défaut déclaré par la [Categorie] de la feature.
///
/// **Garde-fou** : une feature [Categorie#COEUR] est **toujours active** (les flags sont ignorés),
/// car la désactiver casserait l'injecteur ou un écran. Seules les features désactivables
/// (`OPTIONNELLE` / `EXPERIMENTALE`) peuvent être coupées.
public final class Fonctionnalites {

    /// Préfixe de la propriété système par feature : `vigiechiro.features.<id>`.
    static final String PREFIXE_PROP = "vigiechiro.features.";

    /// Propriété système rétro-compatible (noms **simples** de classes, séparés par des virgules).
    static final String PROP_DESACTIVEES = "vigiechiro.features.desactivees";

    /// Clé du flag persisté d'une feature dans `app_setting` : `feature.<id>.active`.
    ///
    /// **L'identifiant d'une fonctionnalité est une donnée persistée, pas un simple nom de code.** Le
    /// renommer laisse en base une clé que plus personne ne lit, et la fonctionnalité reprend sa valeur
    /// par défaut : celle qu'un utilisateur avait désactivée **réapparaît**, sans erreur ni journal.
    /// C'est le mode de défaillance qui a fait fermer #1537 (renommer la feature « lot »).
    ///
    /// Une migration ne suffit pas à réparer ce renommage : les flags sont lus **avant** que les
    /// migrations ne s'appliquent (`RacineInjecteur.creer()` précède `MigrationSchema.migrer()` dans
    /// `App` comme dans `Cli`), donc elle arriverait trop tard et le premier lancement ignorerait le
    /// choix de l'utilisateur (#2187). Un **alias en lecture**, déclaré à côté de l'identifiant, ne
    /// dépend lui d'aucun ordre de démarrage.
    static final String PREFIXE_CLE = "feature.";

    static final String SUFFIXE_CLE = ".active";

    private Fonctionnalites() {}

    /// Toutes les [Fonctionnalite] déclarées par les modules de feature découverts (`ServiceLoader`),
    /// triées par id (ordre déterministe). Exposé pour l'UI de gestion des fonctionnalités.
    public static List<Fonctionnalite> toutes() {
        return ServiceLoader.load(ModuleDeFeature.class).stream()
                .map(provider -> provider.get().fonctionnalite())
                .sorted(Comparator.comparing(Fonctionnalite::id))
                .toList();
    }

    /// Prédicat « feature active » appliqué aux modules découverts par `RacineInjecteur.modules()`.
    /// Les sources (propriétés système, flags persistés pré-bootstrap) sont lues **une fois**, puis
    /// la décision est prise par module.
    static Predicate<ModuleDeFeature> filtreActives() {
        Set<String> desactiveesLegacy = desactiveesLegacy();
        Optional<ReglagesDao> flags = ouvrirFlagsPersistes();
        return module ->
                estActive(module.fonctionnalite(), module.getClass().getSimpleName(), desactiveesLegacy, flags);
    }

    private static boolean estActive(
            Fonctionnalite fonctionnalite,
            String nomClasseModule,
            Set<String> desactiveesLegacy,
            Optional<ReglagesDao> flags) {
        // Garde-fou : une feature COEUR est toujours active (flags ignorés).
        if (fonctionnalite.categorie() == Categorie.COEUR) {
            return true;
        }
        // 1. Propriété système par feature.
        String prop = System.getProperty(PREFIXE_PROP + fonctionnalite.id());
        if (prop != null) {
            return estOui(prop);
        }
        // 2. Alias rétro-compatible (noms de classes simples).
        if (desactiveesLegacy.contains(nomClasseModule)) {
            return false;
        }
        // 3. Flag persisté (app_setting), tolérant.
        Optional<Boolean> persiste = flags.flatMap(dao -> lirePersiste(dao, fonctionnalite.id()));
        if (persiste.isPresent()) {
            return persiste.get();
        }
        // 4. Défaut déclaré par la catégorie.
        return fonctionnalite.categorie().activeParDefaut();
    }

    private static boolean estOui(String valeur) {
        String v = valeur.trim().toLowerCase(Locale.ROOT);
        return v.equals("on") || v.equals("true") || v.equals("1") || v.equals("oui");
    }

    private static Set<String> desactiveesLegacy() {
        String propriete = System.getProperty(PROP_DESACTIVEES, "").trim();
        if (propriete.isEmpty()) {
            return Set.of();
        }
        return Arrays.stream(propriete.split(","))
                .map(String::trim)
                .filter(nom -> !nom.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    /// Ouvre l'accès aux flags persistés du workspace courant **sans créer** de base : renvoie vide si
    /// le fichier SQLite n'existe pas encore (1er lancement).
    private static Optional<ReglagesDao> ouvrirFlagsPersistes() {
        Workspace workspace = Workspace.resolu();
        if (!Files.exists(workspace.cheminBaseDeDonnees())) {
            return Optional.empty();
        }
        return Optional.of(new ReglagesDao(new SourceDeDonnees(workspace)));
    }

    /// Valeur booléenne du flag `feature.<id>.active`, ou vide s'il n'a jamais été écrit **ou** si la
    /// base n'est pas (encore) exploitable (table absente sur une base non migrée).
    private static Optional<Boolean> lirePersiste(ReglagesDao dao, String id) {
        try {
            return dao.lire(PREFIXE_CLE + id + SUFFIXE_CLE).map(Boolean::parseBoolean);
        } catch (DataAccessException erreur) {
            return Optional.empty();
        }
    }
}
