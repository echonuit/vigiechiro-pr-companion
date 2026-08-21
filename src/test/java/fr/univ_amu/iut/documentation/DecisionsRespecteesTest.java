package fr.univ_amu.iut.documentation;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Garde-fous des décisions **vérifiables de façon certaine**.
///
/// ## Pourquoi cette classe existe
///
/// Une ADR énonce une règle, puis vit sa vie. Rien ne dit, six mois plus tard, si le code la respecte
/// encore : il faudrait relire, et on ne relit pas 49 décisions à chaque chantier. Les ADR déclarent
/// donc désormais **comment** elles sont vérifiées, et celles dont l'invariant se prouve pointent vers
/// un test d'ici.
///
/// ## Ce qui n'y entre pas, et pourquoi
///
/// Toutes les décisions ne peuvent pas y figurer. « Le plan précède l'écriture » ou « la mesure fait
/// foi en CI » sont des décisions de méthode : aucun scan ne dira si elles sont tenues. Leur coller un
/// contrôle fabriquerait un test creux, c'est-à-dire pire que rien - le vert qu'il afficherait ne
/// mesurerait aucun fait.
///
/// Deux conditions pour entrer ici : l'invariant se formule comme un motif observable, et il **tient
/// déjà** sur le dépôt. Un invariant violé le jour de sa naissance n'est pas une garde, c'est une dette
/// déguisée en test rouge : il part en `probable`, avec un script de suspects et un cliquet
/// (`scripts/adr/`).
///
/// L'ADR 0035 a fait le trajet inverse pendant l'écriture de cette classe : classée « certaine » sur
/// une mesure qui annonçait zéro infraction, elle s'est révélée en compter - la mesure scannait
/// `src/main/resources`, où il n'y a aucun FXML. D'où la garde de non-vacuité que porte chaque test qui
/// balaie des fichiers : **un test qui ne trouve rien à examiner doit échouer**, pas passer.
class DecisionsRespecteesTest {

    /// Surefire s'exécute depuis la racine du projet : les chemins sont relatifs à elle.
    private static final Path POM = Path.of("pom.xml");

    /// Les propriétés du lanceur de ligne de commande, et celles qui y ajoutent la console sous
    /// Windows (ADR 4071). Deux fichiers, parce que `win-console` ne s'applique qu'à l'un des deux.
    private static final Path LANCEUR_CLI = Path.of("jpackage", "lanceur-cli.properties");

    private static final Path LANCEUR_CLI_WINDOWS = Path.of("jpackage", "lanceur-cli-windows.properties");

    /// Les scripts d'installation du `.deb`, que le dépôt fournit à la place de ceux de jpackage
    /// (ADR 4071) : c'est la seule prise pour poser la commande dans le PATH.
    private static final Path POSTINST_DEB = Path.of("jpackage", "deb", "postinst");

    private static final Path POSTRM_DEB = Path.of("jpackage", "deb", "postrm");

    /// Le manifeste Flatpak : son script de lancement tient le rôle que le `.cfg` jpackage tient
    /// ailleurs, c'est-à-dire déclarer le mot qui ouvre la fenêtre (ADR 4071).
    private static final Path MANIFESTE_FLATPAK = Path.of("flatpak", "fr.echonuit.VigieChiroCompanion.yml");

    /// La racine des features : un sous-paquet par feature, plus `commun`.
    private static final Path SOURCES = Path.of("src", "main", "java", "fr", "univ_amu", "iut");

    /// Un `import` visant le code du projet, dont on retient le premier segment : la feature visée.
    private static final Pattern IMPORT_INTERNE =
            Pattern.compile("^import (?:static )?fr\\.univ_amu\\.iut\\.(\\w+)\\.", Pattern.MULTILINE);

    /// Une inclusion de sous-vue, dont on retient la ressource incluse (relative au FXML qui l'inclut).
    private static final Pattern INCLUSION_FXML = Pattern.compile("<fx:include[^>]*source=\"([^\"]+)\"");

    /// La classe déclarée `fx:controller` d'un FXML.
    private static final Pattern CONTROLLER_FXML = Pattern.compile("fx:controller=\"([^\"]+)\"");

    /// Un porteur de test fabriqué sur place. Les `*Modifiable` du dépôt (confirmation, compte rendu,
    /// désignation de fichier) ne sont pas injectés mais construits en initialiseur de champ : c'est la
    /// forme que `@Inject` ne verrait pas, et pourtant la même faute (ADR 0010 + 2745, #3335).
    private static final Pattern PORTEUR_FABRIQUE = Pattern.compile("new (\\w+Modifiable)\\s*\\(");

    @Test
    @DisplayName("ADR 0038 : l'échelle de sévérité compte quatre niveaux, et son ordre porte la sémantique")
    void l_echelle_de_severite_a_quatre_niveaux_dans_l_ordre() {
        // L'ordre de DÉCLARATION est la sémantique : il gouverne les comparaisons et le classement des
        // constats. Ajouter un niveau au milieu, ou en déplacer un, change silencieusement le sens de
        // tout code qui compare deux sévérités.
        assertThat(Severite.values())
                .as("L'ADR 0038 fixe quatre niveaux, dans cet ordre précis : AVERTISSEMENT s'insère entre "
                        + "INFO et ERREUR parce que l'opération a abouti mais mérite l'attention. Toute "
                        + "insertion ou permutation déplace le sens des comparaisons existantes.")
                .containsExactly(Severite.SUCCES, Severite.INFO, Severite.AVERTISSEMENT, Severite.ERREUR);
    }

    @Test
    @DisplayName("ADR 0045 : l'installeur Windows porte ses constantes d'identité")
    void l_installeur_windows_porte_ses_constantes_d_identite() {
        // Sans --win-upgrade-uuid, jpackage tire un UUID ALÉATOIRE à chaque build (JDK-8214564) : chaque
        // version devient un produit distinct, rien ne désinstalle l'ancienne, et `winget upgrade`
        // installe à côté au lieu de remplacer. La panne serait invisible ici, et visible chez
        // l'utilisateur des mois plus tard.
        String pom = lire(POM);

        assertThat(pom)
                .as("Le profil jpackage-windows doit figer l'UpgradeCode : sans lui, jpackage en tire un "
                        + "au hasard à chaque build, et les montées de version cessent de remplacer "
                        + "l'installation précédente.")
                .contains("--win-upgrade-uuid")
                .contains("0328d083-bdf7-4e84-95bf-918249478c00");

        assertThat(pom)
                .as("Le scope de l'installeur est l'autre constante d'identité : passer de per-user à "
                        + "per-machine ferait cohabiter deux installations chez l'utilisateur.")
                .contains("--win-per-user-install");
    }

    @Test
    @DisplayName("ADR 0047 : l'installeur porte l'identité de distribution Echonuit")
    void l_installeur_porte_l_identite_echonuit() {
        // 0045 garde les constantes d'UPGRADE (UpgradeCode, scope) ; 0047 garde les constantes
        // d'IDENTITÉ : le nom du produit, l'éditeur, le préfixe d'app-id. Distinctes parce qu'un
        // renommage d'identité (retour à « VigieChiro PR » ou à un éditeur nominatif) ne toucherait
        // pas les premières. Ce test ne couvre que le versant pom ; les autres canaux (winget,
        // Flatpak) portent la même identité, gardée ailleurs.
        String pom = lire(POM);

        // Chaque identité est cherchée comme ARGUMENT jpackage, pas comme simple sous-chaîne : le pom
        // mentionne « Echonuit » aussi en commentaire, et un test qui s'en contenterait resterait vert
        // sur un vrai renommage d'éditeur. On vise donc l'argument, là où le renommage frapperait.
        assertThat(pom)
                .as("L'ADR 0047 fixe le nom du produit (« VigieChiro Companion », sans « PR »), son "
                        + "éditeur (le projet Echonuit) et le préfixe d'app-id `fr.echonuit`. Le profil "
                        + "jpackage doit les porter tels quels.")
                .contains("<argument>VigieChiroCompanion</argument>")
                .contains("<argument>Echonuit</argument>")
                .contains("fr.echonuit.VigieChiroCompanion");
    }

    @Test
    @DisplayName("ADR 4071 : le lanceur de ligne de commande est déclaré, et la console reste à Windows")
    void le_lanceur_de_ligne_de_commande_est_declare() {
        // Trois faits tiennent l'accessibilité de la CLI depuis un produit installé, et deux d'entre eux
        // se défont sans que rien ne plante : le mot qui déclare la fenêtre, l'enveloppe qui porte le
        // nom qu'on tape, et la clé `arguments` qui EMPÊCHE cette enveloppe d'hériter du mot. Ce dernier
        // point est le piège du chantier : mesuré sur une app-image, un second lanceur sans clé
        // `arguments` reçoit l'`ihm` du principal, donc `vigiechiro` tapé seul ouvre la fenêtre au lieu
        // de rendre l'aide. Aucune lecture de documentation ne l'annonçait.
        String pom = lire(POM);

        assertThat(pom)
                .as("Le mot qui déclare la fenêtre doit être écrit DANS le paquet : sans lui, le "
                        + "double-clic n'ouvre plus rien, puisque `Launcher` ne devine pas.")
                .contains("<argument>--arguments</argument>")
                .contains("<argument>ihm</argument>");

        assertThat(pom)
                .as("Sans ce second lanceur, la ligne de commande n'a plus de nom à taper : le seul "
                        + "exécutable serait `bin/VigieChiroCompanion`, que personne ne tape sous Unix.")
                .contains("<argument>--add-launcher</argument>")
                .contains("<argument>vigiechiro=${jpackage.lanceurCli}</argument>");

        String commun = lire(LANCEUR_CLI);
        String windows = lire(LANCEUR_CLI_WINDOWS);

        assertThat(commun)
                .as("Le fichier de propriétés du lanceur de ligne de commande doit poser `arguments`, "
                        + "sans quoi il HÉRITE du `ihm` du lanceur graphique et ouvre une fenêtre.")
                .contains("arguments=--help");
        assertThat(windows)
                .as("Même exigence sous Windows, et elle y compte davantage : c'est là que le lanceur "
                        + "est appelé depuis une console.")
                .contains("arguments=--help");

        assertThat(windows)
                .as("La console est la raison d'être de l'enveloppe sous Windows : sans elle, la "
                        + "commande n'écrit nulle part et rend 0, panne indiscernable d'un succès.")
                .contains("win-console=true");
        assertThat(commun)
                .as("Et elle ne se déclare QUE sous Windows : le fichier commun sert aussi macOS et "
                        + "Linux, où la propriété n'a pas de sens.")
                .doesNotContain("win-console");

        assertThat(pom)
                .as("La console ne doit jamais être demandée pour TOUS les lanceurs : le lanceur "
                        + "graphique en ouvrirait une à chaque lancement depuis le menu Démarrer.")
                .doesNotContain("<argument>--win-console</argument>");
    }

    @Test
    @DisplayName("ADR 4071 : le Flatpak déclare lui aussi le mot qui ouvre la fenêtre")
    void le_flatpak_declare_le_mot_qui_ouvre_la_fenetre() {
        // Le Flatpak n'a pas de `.cfg` jpackage : son script de lancement en tient le rôle, et cette
        // ligne décide seule de ce que fait `flatpak run <app-id>` sans argument - donc de ce que fait
        // l'entrée de menu. Repassée à `"$@"`, elle rendrait l'aide de la CLI au double-clic.
        //
        // ⚠️ Rien d'autre ne le verrait. Le garde voisin, `verifie-affichage-flatpak.sh`, ne regarde que
        // les sockets d'affichage, et le paquet lui-même n'est construit ni sur les PR ni sur `main` :
        // il l'est chez Flathub, à partir du `.deb` publié. Le défaut ne se découvrirait donc que sur le
        // poste d'un utilisateur.
        String manifeste = lire(MANIFESTE_FLATPAK);

        assertThat(manifeste)
                .as("Le script de lancement du Flatpak doit poser `ihm` par défaut, faute de quoi le "
                        + "double-clic et `flatpak run <app-id>` rendent l'aide au lieu d'ouvrir la "
                        + "fenêtre. La forme POSIX est exigée telle quelle : le runtime freedesktop "
                        + "n'a pas bash.")
                .contains("fr.univ_amu.iut.Launcher \"${@:-ihm}\"");
    }

    @Test
    @DisplayName("ADR 4071 : le paquet Debian pose la commande dans le PATH, sans perdre l'entrée de menu")
    void le_paquet_debian_pose_la_commande_dans_le_path() {
        // Reprendre le postinst de jpackage, c'est reprendre sa charge : il installait l'entrée de menu,
        // et rien à la construction ne dirait qu'on l'a perdue. Le paquet se construirait, s'installerait,
        // et l'application aurait simplement disparu du menu des applications - défaut qu'on ne voit
        // qu'en installant sur un poste de bureau.
        String pom = lire(POM);

        assertThat(pom)
                .as("Sans `resource-dir`, jpackage reprend SES scripts et le lien n'est plus posé : la "
                        + "commande retourne vivre sous /opt, atteignable en chemin complet seulement.")
                .contains("<argument>--resource-dir</argument>")
                .contains("<argument>${project.basedir}/jpackage/deb</argument>");

        String postinst = lire(POSTINST_DEB);

        assertThat(postinst)
                .as("Le postinst du dépôt REMPLACE celui de jpackage : l'entrée de menu qu'il installait "
                        + "doit continuer d'être installée ici.")
                .contains("xdg-desktop-menu install");

        // ⚠️ Et l'appel doit être TOLÉRÉ, pas nu (#4081). Sous `set -e`, son code 3 - rendu partout où
        // aucun menu système n'est inscriptible - laissait le paquet en `half-configured` : installé,
        // non configuré, bloquant les opérations `apt` suivantes. Un geste d'affichage n'a pas à
        // décider du sort de l'installation entière.
        assertThat(postinst)
                .as("L'installation de l'entrée de menu doit être tolérée et ANNONCÉE, faute de quoi son "
                        + "échec emporte tout le postinst - et le paquet reste `iF`.")
                .contains("if ! xdg-desktop-menu install")
                .contains("entrée de menu non posée");
        assertThat(postinst)
                .as("C'est la raison d'être de ce script : poser `vigiechiro` dans le PATH, faute de "
                        + "quoi le `.deb` n'installe aucun exécutable hors de /opt.")
                .contains("ln -sf /opt/vigiechirocompanion/bin/vigiechiro /usr/bin/vigiechiro");

        // ⚠️ L'ordre est un fait mesuré, pas une préférence : sur un système sans dossier de menus
        // inscriptible, `xdg-desktop-menu` rend 3 et le `set -e` arrête le script sur-le-champ. Le lien
        // placé après n'était alors pas posé, et l'installation rendait une commande absente.
        assertThat(postinst.indexOf("ln -sf /opt/vigiechirocompanion/bin/vigiechiro"))
                .as("Le lien doit être posé AVANT l'entrée de menu : `xdg-desktop-menu` peut échouer, "
                        + "et `set -e` emporterait alors tout ce qui le suit.")
                .isLessThan(postinst.indexOf("xdg-desktop-menu install"));

        assertThat(lire(POSTRM_DEB))
                .as("Ce que l'installation pose, la désinstallation le retire : sans cela, un lien mort "
                        + "reste dans le PATH après le départ du paquet.")
                .contains("rm -f /usr/bin/vigiechiro");
    }

    @Test
    @DisplayName("ADR 0004 : aucun cycle entre features ; les ponts passent par un port dans commun")
    void aucun_cycle_entre_les_features() {
        Map<String, Set<String>> arcs = dependancesEntreFeatures();

        // Sans cette garde, un chemin de sources devenu faux rendrait le test vert en n'examinant rien.
        // C'est très exactement ce qui a fait croire l'ADR 0035 respectée : son scan lisait un dossier
        // sans FXML.
        assertThat(arcs)
                .as("aucune dépendance entre features trouvée : le test n'examine rien et ne prouve rien")
                .isNotEmpty();

        List<String> cycles = new ArrayList<>();
        for (String depart : new TreeSet<>(arcs.keySet())) {
            chercherCycle(arcs, depart, new ArrayList<>(List.of(depart)), cycles);
        }

        assertThat(cycles)
                .as("Ces features se dépendent mutuellement. L'ADR 0004 l'interdit : un cycle empêche de "
                        + "désactiver l'une sans l'autre, et fait dépendre chacune de l'ordre d'initialisation "
                        + "de sa voisine. Le pont doit passer par un port neutre dans `commun`, consommé des "
                        + "deux côtés.")
                .isEmpty();
    }

    /// Le graphe des dépendances **directes** entre features, lu dans les `import`.
    ///
    /// `commun` est exclu : il est précisément le lieu où les ponts sont autorisés à passer, donc y
    /// dépendre n'est jamais un cycle au sens de l'ADR.
    private static Map<String, Set<String>> dependancesEntreFeatures() {
        Set<String> features = sousPaquets();
        Map<String, Set<String>> arcs = new TreeMap<>();
        try (Stream<Path> sources = Files.walk(SOURCES)) {
            for (Path source :
                    sources.filter(f -> f.toString().endsWith(".java")).toList()) {
                String feature = SOURCES.relativize(source).getName(0).toString();
                if (!features.contains(feature)) {
                    continue;
                }
                Matcher imports = IMPORT_INTERNE.matcher(lire(source));
                while (imports.find()) {
                    String vise = imports.group(1);
                    if (features.contains(vise) && !vise.equals(feature)) {
                        arcs.computeIfAbsent(feature, f -> new TreeSet<>()).add(vise);
                    }
                }
            }
        } catch (IOException echec) {
            throw new UncheckedIOException("parcours de " + SOURCES, echec);
        }
        return arcs;
    }

    private static Set<String> sousPaquets() {
        try (Stream<Path> entrees = Files.list(SOURCES)) {
            return entrees.filter(Files::isDirectory)
                    .map(dossier -> dossier.getFileName().toString())
                    .filter(nom -> !"commun".equals(nom))
                    .collect(Collectors.toCollection(TreeSet::new));
        } catch (IOException echec) {
            throw new UncheckedIOException("liste de " + SOURCES, echec);
        }
    }

    private static void chercherCycle(
            Map<String, Set<String>> arcs, String courant, List<String> chemin, List<String> cycles) {
        for (String suivant : arcs.getOrDefault(courant, Set.of())) {
            int boucle = chemin.indexOf(suivant);
            if (boucle >= 0) {
                cycles.add(String.join(" -> ", chemin.subList(boucle, chemin.size())) + " -> " + suivant);
                continue;
            }
            List<String> prolonge = new ArrayList<>(chemin);
            prolonge.add(suivant);
            chercherCycle(arcs, suivant, prolonge, cycles);
        }
    }

    @Test
    @DisplayName("ADR 0005 : « archivé » n'est pas un statut de workflow, StatutWorkflow ne le porte pas")
    void archive_n_est_pas_un_statut_de_workflow() {
        // StatutWorkflow est une progression MONOTONE qui se termine à DEPOSE. « Archivé » est un état
        // OBSERVÉ (le passage a disparu de la plateforme), vérifié par une cascade de preuves - pas une
        // étape de plus dans l'énum. Ajouter ARCHIVE ici ferait croire à un statut qui progresse, alors
        // que rien ne « progresse » vers l'archivage : c'est un constat, pas une transition.
        List<String> valeurs =
                Arrays.stream(StatutWorkflow.values()).map(Enum::name).toList();

        assertThat(valeurs)
                .as("StatutWorkflow ne doit contenir aucune valeur évoquant l'archivage : l'ADR 0005 pose "
                        + "que « archivé » est un état observé, pas un statut de workflow. Une telle valeur "
                        + "réintroduirait précisément la confusion que la décision a écartée.")
                .noneMatch(nom -> nom.contains("ARCHIV"));
    }

    @Test
    @DisplayName("ADR 2745 : une sous-vue ne se procure rien de ce qui doit être unique")
    void une_sous_vue_ne_se_procure_pas_ce_qui_doit_etre_unique() {
        // Le piège que cette garde ferme a réellement eu lieu, au premier fx:include du dépôt (#2745).
        // Un controller de sous-vue avec un constructeur @Inject se construit très bien : FXMLLoader
        // propage la controllerFactory Guice aux inclusions. Mais les ViewModel du dépôt sont
        // délibérément NON-SINGLETON (« un VM frais par chargement d'écran »), si bien que la sous-vue
        // reçoit un SECOND modèle, vide, et câble ses nœuds dessus.
        //
        // Rien ne rougit : ça compile, la vue se charge, l'écran s'affiche. La table est simplement
        // vide et les actions ne portent sur rien.
        //
        // ⚠️ Le ViewModel n'est qu'un cas (#3335). Un PORTEUR de dialogue en est un autre, et il est
        // plus piégeux parce qu'il ne s'injecte pas : le dépôt le fabrique en initialiseur de champ
        // (`new ConfirmateurModifiable()`, neuf contrôleurs le font). Or l'ADR 0010 en fait un point de
        // SUBSTITUTION pour les tests, et un point de substitution n'en est un que s'il est unique : un
        // test parent qui pose son double sur `controleur.confirmateur()` ne toucherait pas celui de la
        // sous-vue, et le `showAndWait()` figerait le test headless.
        //
        // Même panne que l'ADR 3018 à un autre étage : un composant se procure localement ce qu'il
        // aurait dû recevoir, et le résultat n'a pas l'air cassé.
        Map<String, String> fautifs = new TreeMap<>();
        Set<String> inclusions = new TreeSet<>();

        for (Path fxml : fichiers(SOURCES, ".fxml")) {
            Matcher inclusion = INCLUSION_FXML.matcher(lire(fxml));
            while (inclusion.find()) {
                Path incluse = fxml.getParent().resolve(inclusion.group(1));
                inclusions.add(incluse.getFileName().toString());
                Matcher controller = CONTROLLER_FXML.matcher(lire(incluse));
                if (!controller.find()) {
                    continue; // Une inclusion peut être purement décorative : pas de controller, rien à juger.
                }
                String classe = controller.group(1);
                Path source =
                        SOURCES.resolve(classe.replace("fr.univ_amu.iut.", "").replace('.', '/') + ".java");
                if (!Files.exists(source)) {
                    continue;
                }
                String code = lire(source);
                Matcher porteur = PORTEUR_FABRIQUE.matcher(code);
                if (code.contains("@Inject")) {
                    fautifs.put(incluse.getFileName().toString(), classe + " : constructeur @Inject");
                } else if (porteur.find()) {
                    fautifs.put(
                            incluse.getFileName().toString(), classe + " : fabrique son propre " + porteur.group(1));
                }
            }
        }

        // Non-vacuité : sans inclusion trouvée, ce test passerait au vert en n'ayant rien examiné, et
        // annoncerait une règle tenue que personne ne vérifierait plus (cf. l'ADR 0035 en tête de classe).
        assertThat(inclusions)
                .as(
                        "Aucun fx:include trouvé sous %s : ce test n'aurait rien examiné. Soit les inclusions "
                                + "ont disparu, soit le motif de détection a cessé de correspondre - dans les deux "
                                + "cas, le vert de cette garde ne mesurerait plus rien.",
                        SOURCES)
                .isNotEmpty();

        assertThat(fautifs)
                .as("Ces controllers de sous-vue se procurent ce que leur parent doit leur passer "
                        + "(ADR 2745). Un ViewModel obtenu ici est un SECOND modèle, vide ; un porteur "
                        + "fabriqué ici est un SECOND point de substitution, que les doubles des tests "
                        + "parents n'atteindront pas (ADR 0010). Dans les deux cas l'écran se charge sans "
                        + "rien signaler. Passez-les en paramètres d'une méthode « installer(...) » "
                        + "appelée depuis l'initialize() du parent.")
                .isEmpty();
    }

    /// Les fichiers d'extension donnée sous une racine, triés pour un diagnostic stable.
    private static List<Path> fichiers(Path racine, String extension) {
        try (Stream<Path> chemins = Files.walk(racine)) {
            return chemins.filter(c -> c.getFileName().toString().endsWith(extension))
                    .sorted()
                    .toList();
        } catch (IOException echec) {
            throw new UncheckedIOException("parcours de " + racine, echec);
        }
    }

    private static String lire(Path fichier) {
        try {
            return Files.readString(fichier);
        } catch (IOException echec) {
            throw new UncheckedIOException("lecture de " + fichier, echec);
        }
    }
}
