package fr.univ_amu.iut.documentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import fr.univ_amu.iut.cli.commande.CommandeRacine;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.view.ActiviteAccueil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/// Garde-fou de **documentation** (#1458) : une commande CLI sans ligne de doc, ou un écran sans fiche,
/// font **rougir la CI**.
///
/// ## Pourquoi ce test existe
///
/// La clôture de l'EPIC #1154 a trouvé deux dérives que **rien** n'aurait signalées :
/// `dev-docs/cli.md` documentait **22 commandes sur 29** (dont quatre livrées par le chantier même), et
/// l'écran « Audit de cohérence » n'avait **aucune fiche** depuis sa livraison (#1133). Une relecture à
/// la main les a vues. C'est précisément ce qu'on ne peut pas garantir.
///
/// Le dépôt défend déjà ses **captures** par quatre garde-fous (`check-doc-images.sh`,
/// `captures.manifest`, `check-captures.sh`, `check-capture-mains.sh`), au nom d'un principe qu'il a
/// tranché : *« une fonctionnalité visible sans capture est une fonctionnalité à moitié livrée »*
/// (`dev-docs/captures.md`). Les **commandes** et les **écrans**, eux, n'avaient rien. Ce test comble
/// l'asymétrie : le même raisonnement vaut pour une commande sans ligne et pour un écran sans page.
///
/// ## Ce qu'il confronte
///
/// Le point de comparaison n'est **jamais une liste tenue à la main** (c'est exactement ce qui dérive),
/// mais la **vérité du câblage** :
///
/// - les sous-commandes déclarées dans l'annotation `@Command` de [CommandeRacine] — lues par
///   **réflexion sur l'annotation**, sans jamais instancier une commande (leurs constructeurs tirent des
///   `Provider` qui ouvrent la base : les instancier ici ferait de l'E/S pour rien) ;
/// - les [ActiviteAccueil] réellement liées dans le `Multibinder` de l'**injecteur**, pas les classes qui
///   ressemblent à des activités.
///
/// Une doc qui ment est **pire** qu'une doc absente : on la croit.
class DocumentationAJourTest {

    /// Surefire s'exécute depuis la racine du projet (`${basedir}`) : les chemins sont relatifs à elle.
    private static final Path DOC_CLI = Path.of("dev-docs", "cli.md");

    private static final Path FICHES = Path.of("docs", "ecrans");

    private static final Path INDEX_FICHES = FICHES.resolve("index.md");

    private static final Path NAV = Path.of("mkdocs.yml");

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("Chaque sous-commande câblée dans la CLI figure dans le tableau de dev-docs/cli.md")
    void chaque_commande_cli_est_documentee() {
        String doc = lire(DOC_CLI);

        List<String> absentes = sousCommandesCablees().stream()
                .filter(nom -> !doc.contains("| `" + nom + "`"))
                .sorted()
                .toList();

        assertThat(absentes)
                .as("Ces commandes sont câblées dans CommandeRacine mais ne figurent dans aucune ligne du "
                        + "tableau de dev-docs/cli.md. Une commande qu'on ne documente pas est une commande "
                        + "que personne ne trouvera : ajoutez-lui sa ligne.")
                .isEmpty();
    }

    @Test
    @DisplayName("Chaque activité d'accueil déclare une fiche d'écran qui existe")
    void chaque_activite_a_sa_fiche(@TempDir Path espaceDeTravail) {
        Set<ActiviteAccueil> activites = activitesCablees(espaceDeTravail);

        assertThat(activites)
                .as("aucune activité d'accueil câblée : le test ne prouverait rien")
                .isNotEmpty();

        List<String> sansFiche = activites.stream()
                .filter(activite -> !Files.isRegularFile(FICHES.resolve(activite.pageDoc() + ".md")))
                .map(activite -> activite.titre() + " -> docs/ecrans/" + activite.pageDoc() + ".md")
                .sorted()
                .toList();

        assertThat(sansFiche)
                .as("Ces activités sont offertes sur l'écran d'accueil, mais la fiche qu'elles déclarent "
                        + "n'existe pas. Un écran livré sans page est un écran à moitié livré.")
                .isEmpty();
    }

    @Test
    @DisplayName("Chaque fiche d'écran est atteignable : dans la nav MkDocs et dans l'index de la section")
    void chaque_fiche_est_atteignable() {
        String nav = lire(NAV);
        String index = lire(INDEX_FICHES);
        List<String> fiches = fichesEcrites();

        assertThat(fiches)
                .as("aucune fiche d'écran trouvée : le test ne prouverait rien")
                .isNotEmpty();

        List<String> horsNav = fiches.stream()
                .filter(fiche -> !nav.contains("ecrans/" + fiche + ".md"))
                .toList();
        assertThat(horsNav)
                .as("Ces fiches existent mais n'apparaissent dans aucune entrée `nav` de mkdocs.yml : le site "
                        + "produit ne les publie pas. Une page que le site ne sert pas n'existe pas.")
                .isEmpty();

        List<String> horsIndex = fiches.stream()
                .filter(fiche -> !index.contains("(" + fiche + ".md)"))
                .toList();
        assertThat(horsIndex)
                .as("Ces fiches ne sont liées par aucune ligne du tableau de docs/ecrans/index.md : depuis "
                        + "l'index de leur propre section, l'écran est invisible.")
                .isEmpty();
    }

    /// Noms des sous-commandes tels que picocli les expose à l'utilisateur, lus **sur l'annotation** de
    /// [CommandeRacine] : aucune commande n'est instanciée, donc aucun `Provider` n'ouvre la base.
    private static List<String> sousCommandesCablees() {
        CommandLine.Command racine = CommandeRacine.class.getAnnotation(CommandLine.Command.class);
        return Stream.of(racine.subcommands())
                .map(classe -> classe.getAnnotation(CommandLine.Command.class))
                .filter(Objects::nonNull)
                .map(CommandLine.Command::name)
                .toList();
    }

    /// Les activités **réellement liées** dans le `Multibinder<ActiviteAccueil>` : la vérité du câblage,
    /// pas les classes qui en ont l'air.
    private static Set<ActiviteAccueil> activitesCablees(Path espaceDeTravail) {
        System.setProperty("vigiechiro.workspace", espaceDeTravail.toString());
        Injector injecteur = RacineInjecteur.creer();
        return injecteur.getInstance(Key.get(new TypeLiteral<Set<ActiviteAccueil>>() {}));
    }

    /// Noms courts des fiches présentes sur le disque, `index.md` exclue (elle est le sommaire, pas un écran).
    private static List<String> fichesEcrites() {
        try (Stream<Path> pages = Files.list(FICHES)) {
            return pages.map(page -> page.getFileName().toString())
                    .filter(nom -> nom.endsWith(".md"))
                    .filter(nom -> !"index.md".equals(nom))
                    .map(nom -> nom.substring(0, nom.length() - ".md".length()))
                    .sorted(Comparator.naturalOrder())
                    .toList();
        } catch (IOException echec) {
            throw new UncheckedIOException("lecture de " + FICHES, echec);
        }
    }

    private static String lire(Path fichier) {
        try {
            return Files.readString(fichier);
        } catch (IOException echec) {
            throw new UncheckedIOException("lecture de " + fichier, echec);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Journal des ADR (#1881)
    // ---------------------------------------------------------------------------------------------

    private static final Path DECISIONS = Path.of("dev-docs", "decisions");

    private static final Path INDEX_ADR = DECISIONS.resolve("index.md");

    /// Navigation du site **développeur**, distincte de celle du site produit ([#NAV]) : c'est elle qui
    /// publie les ADR.
    private static final Path NAV_DEV = Path.of("mkdocs-dev.yml");

    /// Un fichier d'ADR : au moins quatre chiffres, un tiret, un titre en kebab-case.
    ///
    /// **Au moins** quatre, et non exactement : depuis l'[ADR 1881] le numéro est celui de l'issue du
    /// chantier, qui passera un jour à cinq chiffres. Un motif strict laisserait alors ces ADR
    /// **silencieusement hors de portée** de tous les tests ci-dessous, y compris celui des doublons.
    private static final Pattern FICHIER_ADR = Pattern.compile("^(\\d{4,})-[a-z0-9-]+\\.md$");

    /// Dernier numéro attribué par le compteur global, clos par l'[ADR 1881]. Les ADR 0001 à 0048 le
    /// gardent ; au-delà, le numéro est celui de l'issue du chantier.
    private static final int DERNIER_NUMERO_DE_COMPTEUR = 48;

    /// Les deux numéros qu'une résolution de collision a libérés, et qui restent vides : les combler
    /// ferait pointer vers un numéro qui a déjà voulu dire autre chose dans une PR et une discussion.
    private static final Set<Integer> TROUS_DU_COMPTEUR = Set.of(29, 30);

    /// Plancher des numéros d'issue : aucune issue du dépôt n'est descendue sous ce seuil depuis
    /// longtemps, et le compteur, lui, n'ira jamais jusque-là. Il sépare donc sans ambiguïté les deux
    /// régimes de numérotation.
    private static final int PLANCHER_NUMERO_D_ISSUE = 1000;

    /// L'en-tête d'une ADR, qui doit porter le même numéro que son nom de fichier.
    ///
    /// Deux formes coexistent dans le journal (`# ADR 0035 — …` et `# 0026 - …`) et le test **tolère les
    /// deux** : il garde le **numéro**, pas le style de titre. Le rendre strict sur la forme le ferait
    /// rougir sur une variation de rédaction, ce qui est le meilleur moyen de le faire désactiver.
    private static final Pattern ENTETE_ADR = Pattern.compile("^#\\s+(?:ADR\\s+)?(\\d{4,})\\b");

    @Test
    @DisplayName("Deux ADR ne portent jamais le même numéro")
    void les_numeros_d_adr_sont_uniques() {
        Map<String, List<String>> parNumero = new TreeMap<>();
        for (String fichier : fichiersAdr()) {
            Matcher m = FICHIER_ADR.matcher(fichier);
            if (m.matches()) {
                parNumero
                        .computeIfAbsent(m.group(1), numero -> new ArrayList<>())
                        .add(fichier);
            }
        }
        Map<String, List<String>> collisions = new TreeMap<>();
        parNumero.forEach((numero, fichiers) -> {
            if (fichiers.size() > 1) {
                collisions.put(numero, fichiers);
            }
        });

        assertThat(collisions)
                .as("deux chantiers parallèles ont réservé le même numéro d'ADR. C'est arrivé pour de "
                        + "bon (#1881) : la collision ne se découvre alors qu'au conflit de fusion, sur "
                        + "une branche déjà poussée, dans un index que tout le monde édite. Renumérote "
                        + "la plus récente, en balayant TOUTES les branches distantes et pas seulement "
                        + "`main` : une PR ouverte réserve son numéro sans qu'il apparaisse nulle part.")
                .isEmpty();
    }

    @Test
    @DisplayName("Le numéro écrit dans l'en-tête d'une ADR est celui de son nom de fichier")
    void l_entete_d_une_adr_porte_son_numero() {
        SoftAssertions verifs = new SoftAssertions();
        for (String fichier : fichiersAdr()) {
            Matcher nom = FICHIER_ADR.matcher(fichier);
            if (!nom.matches()) {
                continue;
            }
            String premiereLigne =
                    lire(DECISIONS.resolve(fichier)).lines().findFirst().orElse("");
            Matcher entete = ENTETE_ADR.matcher(premiereLigne);
            verifs.assertThat(entete.find() ? entete.group(1) : null)
                    .as(
                            "%s : l'en-tête annonce « %s » au lieu du numéro du fichier. C'est le piège d'une "
                                    + "renumérotation faite à moitié : le fichier change de nom, l'en-tête reste, "
                                    + "et l'ADR se met à porter deux numéros.",
                            fichier, premiereLigne)
                    .isEqualTo(nom.group(1));
        }
        verifs.assertAll();
    }

    @Test
    @DisplayName("#2082 : chaque ADR est atteignable dans la nav du site dev, pas seulement au journal")
    void chaque_adr_est_dans_la_nav() {
        // Le journal (`index.md`) et la nav (`mkdocs-dev.yml`) sont **deux listes** pour une seule
        // vérité, et seule la première était gardée. C'est ainsi que cinq ADR se sont retrouvées
        // publiées mais introuvables autrement qu'en devinant leur URL : leur ligne de journal existait,
        // leur entrée de nav non. MkDocs ne se plaint que de l'inverse (une entrée sans fichier).
        String nav = lire(NAV_DEV);
        List<String> fichiers = fichiersAdr();

        assertThat(fichiers)
                .as("aucune ADR trouvée : le test ne prouverait rien")
                .isNotEmpty();

        List<String> horsNav = fichiers.stream()
                .filter(fichier -> !nav.contains("decisions/" + fichier))
                .toList();

        assertThat(horsNav)
                .as(
                        "Ces ADR existent et sont au journal, mais n'apparaissent dans aucune entrée `nav` de "
                                + "%s : le site dev les publie sans qu'aucun lien n'y mène. Ajoutez-les sous "
                                + "« Décisions », dans l'ordre de leur numéro.",
                        NAV_DEV)
                .isEmpty();
    }

    @Test
    @DisplayName("Chaque ADR est au journal, et chaque ligne du journal pointe vers une ADR existante")
    void le_journal_des_adr_et_les_fichiers_se_correspondent() {
        String journal = lire(INDEX_ADR);
        List<String> fichiers = fichiersAdr();

        SoftAssertions verifs = new SoftAssertions();
        for (String fichier : fichiers) {
            verifs.assertThat(journal)
                    .as(
                            "%s n'a pas de ligne dans le journal (`%s`) : une ADR absente de l'index est une "
                                    + "décision qu'on ne retrouvera pas.",
                            fichier, INDEX_ADR)
                    .contains(fichier);
        }

        Matcher lien = Pattern.compile("\\((\\d{4,}-[a-z0-9-]+\\.md)\\)").matcher(journal);
        while (lien.find()) {
            verifs.assertThat(fichiers)
                    .as(
                            "le journal renvoie vers « %s », qui n'existe pas. Une renumérotation a laissé "
                                    + "sa ligne derrière elle.",
                            lien.group(1))
                    .contains(lien.group(1));
        }
        verifs.assertAll();
    }

    @Test
    @DisplayName("#1881 : le compteur d'ADR reste clos, personne ne reprend « le premier numéro libre »")
    void le_compteur_d_adr_reste_clos() {
        Set<Integer> attendus = new TreeSet<>();
        for (int numero = 1; numero <= DERNIER_NUMERO_DE_COMPTEUR; numero++) {
            if (!TROUS_DU_COMPTEUR.contains(numero)) {
                attendus.add(numero);
            }
        }

        Set<Integer> presents = new TreeSet<>();
        for (String fichier : fichiersAdr()) {
            Matcher nom = FICHIER_ADR.matcher(fichier);
            if (nom.matches() && Integer.parseInt(nom.group(1)) < PLANCHER_NUMERO_D_ISSUE) {
                presents.add(Integer.parseInt(nom.group(1)));
            }
        }

        assertThat(presents)
                .as("la série numérotée au compteur a bougé. Elle est close depuis l'ADR 1881 : une ADR"
                        + " neuve prend le numéro de l'issue de son chantier, pas « le premier numéro"
                        + " libre ». Un numéro en trop, c'est le compteur qui rouvre, et avec lui les"
                        + " collisions entre chantiers parallèles ; un numéro en moins, c'est une ADR"
                        + " effacée ou un des deux trous (0029, 0030) comblé, alors qu'ils désignent"
                        + " déjà autre chose dans une PR et une discussion.")
                .isEqualTo(attendus);
    }

    @Test
    @DisplayName("#1881 : une ADR postérieure à la bascule porte le numéro de son chantier")
    void une_adr_recente_porte_le_numero_de_son_chantier() {
        SoftAssertions verifs = new SoftAssertions();
        for (String fichier : fichiersAdr()) {
            Matcher nom = FICHIER_ADR.matcher(fichier);
            if (!nom.matches()) {
                continue;
            }
            int numero = Integer.parseInt(nom.group(1));
            if (numero < PLANCHER_NUMERO_D_ISSUE) {
                continue;
            }
            String chantier = lire(DECISIONS.resolve(fichier))
                    .lines()
                    .filter(ligne -> ligne.startsWith("- **Chantier**"))
                    .findFirst()
                    .orElse("");
            verifs.assertThat(chantier)
                    .as(
                            "%s : sa ligne « Chantier » ne cite pas #%d. Depuis l'ADR 1881, le numéro d'une"
                                    + " ADR n'est pas un rang mais une référence : il doit désigner l'issue qui"
                                    + " porte la décision, sans quoi il ne renvoie à aucune discussion.",
                            fichier, numero)
                    .contains("#" + numero);
        }
        verifs.assertAll();
    }

    /// Les fichiers d'ADR présents sur le disque, `index.md` exclue (c'est le journal, pas une décision).
    private static List<String> fichiersAdr() {
        try (Stream<Path> pages = Files.list(DECISIONS)) {
            return pages.map(page -> page.getFileName().toString())
                    .filter(nom -> nom.endsWith(".md"))
                    .filter(nom -> !"index.md".equals(nom))
                    .sorted(Comparator.naturalOrder())
                    .toList();
        } catch (IOException echec) {
            throw new UncheckedIOException("lecture de " + DECISIONS, echec);
        }
    }
}
