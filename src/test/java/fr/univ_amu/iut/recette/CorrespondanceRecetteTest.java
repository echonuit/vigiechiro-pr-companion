package fr.univ_amu.iut.recette;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/// Le garde de correspondance entre les sessions de recette et le code qui les couvre (#3728).
///
/// ## Pourquoi il existe
///
/// Une session est un document en prose ; le harnais est du code. Ces deux-là divergent, et le
/// jour où ils divergent, le code atteste ce que la session ne dit plus. Ce n'est pas une crainte
/// théorique : `s1-premier-contact.md` désignait les cas 24 et 25 là où il parlait des 26 et 27.
///
/// ## Ses TROIS devoirs, et le deuxième est celui qu'on oublie
///
/// 1. tout identifiant cité par un test **existe** dans une session ;
/// 2. les cas que **rien** ne couvre sont **listés** ;
/// 3. le script et le code disent la **même chose** du juge (#3764).
///
/// ⚠️ Sans le deuxième, un garde qui ne trouve rien à redire est **indiscernable** d'un garde qui ne
/// lit pas le document. Le premier devoir seul serait vert sur un dépôt où aucun test ne cite quoi
/// que ce soit.
///
/// « Aucun test » reste parfaitement recevable : c'est même le cas normal pour les cas perceptifs,
/// que la passe 6 envoie en recette **parce qu'**ils ne sont pas automatisables. Ce garde ne
/// réclame donc pas la couverture, il refuse qu'elle soit **tacite**.
///
/// ## Ce que le troisième devoir a changé
///
/// Les cas perceptifs étaient jusqu'ici indiscernables des cas non couverts : deux bacs pour trois
/// situations. Le script les marque désormais `*perceptif*`, ce qui leur donne le leur, et surtout
/// permet de **confronter** les deux sources - voir [RepartitionDesCas], où le tri vit pour pouvoir
/// être éprouvé sur des situations que ce dépôt ne contient pas.
class CorrespondanceRecetteTest {

    private static final Path SESSIONS = Path.of("dev-docs", "recette", "sessions");

    /// Les deux pages qui portent les lecteurs, et donc les seules où une réserve peut être lue.
    private static final Path PAGE_PERCEPTIFS = Path.of("dev-docs", "recette", "clips-perceptifs.md");

    private static final Path PAGE_ASSERTES = Path.of("dev-docs", "recette", "clips-assertes.md");

    /// Les sessions dont ce garde ne lit aucun cas, et dont le silence est assumé (#3884).
    ///
    /// ⚠️ Cette liste est une **dette chiffrée**, pas une dispense. Chaque ligne dit pourquoi la
    /// session échappe à la regex, et disparaîtra le jour où elle adoptera le format `- **Sxx-NN** ·`.
    /// Le garde refuse qu'on l'oublie dans les deux sens : une session muette absente d'ici rougit,
    /// une ligne d'ici dont la session s'est mise à parler rougit aussi.
    ///
    /// Le travail de conversion est le point 2 de #3884, et il se fait session par session.
    ///
    /// ⚠️ `Map.ofEntries` et non `Map.of` : ce dernier plafonne à **dix** paires, et la liste en
    /// compte exactement dix. La onzième session muette - celle que ce garde existe pour attraper -
    /// aurait cassé la **compilation** au lieu de rougir avec son message. Un garde qui échoue par
    /// erreur de build ne dit pas ce qu'il a trouvé, et c'est ce qui est arrivé en écrivant ceci.
    private static final Map<String, String> MUETTES_ADMISES = Map.ofEntries(
            Map.entry("passe-ciblee-constats-en-attente.md", "cas préfixés PC- et cochés, hors numérotation"),
            Map.entry(
                    "passe-verification-stabilisation.md",
                    "ce n'est pas une session : un tableau des capacités et de leur session propriétaire"));

    /// Les classes à filmer pour une **tournage complet** (#3835), déposées là où le script de
    /// séance ira les chercher.
    ///
    /// ⚠️ Cette liste se DÉRIVE, elle ne se tient pas à la main. Un `grep` sur `@CasDeRecette`
    /// ramène deux faux positifs sur dix-huit - l'annotation elle-même, qui contient un exemple
    /// dans sa documentation, et les fixtures de [ReperesDeSeanceTest]. Le balayage du classpath,
    /// lui, voit les annotations compilées et honore [FixtureDeRecette].
    ///
    /// Une liste tenue à la main dériverait exactement comme la prose dérivait avant #3728.
    private static final Path CLASSES_A_FILMER = Path.of("target", "recette", "classes-citantes.txt");

    /// Les classes à filmer, rangées par SESSION (#4163).
    ///
    /// ⚠️ Un fichier à part, et non une colonne de plus dans le précédent : celui-ci est lu par
    /// `lance-test-filme.sh --planche`, qui le passe tel quel à `paste -sd,`. Y ajouter une colonne
    /// ferait filmer des classes dont le nom porterait un numéro de session.
    private static final Path SESSIONS_A_FILMER = Path.of("target", "recette", "sessions-a-filmer.tsv");

    /// Le nombre de cas que cite le corpus **connecté**, dérivé comme les autres (#4326).
    ///
    /// ⚠️ Un fichier à part, et non une colonne de plus : le corpus connecté se choisit par un TAG, pas
    /// par une session, et ses cas appartiennent aux sessions ordinaires. Les mêler ferait compter deux
    /// fois un cas joué des deux façons.
    ///
    /// Sans lui, le tournage connecté ne pouvait vérifier que « au moins un cas est sorti » - le genre
    /// de garde qui ne garde rien dès que le corpus dépasse un scénario.
    private static final Path CAS_CONNECTES = Path.of("target", "recette", "cas-connectes.txt");

    /// L'étiquette qui désigne un scénario tourné contre la plateforme réelle (#4307).
    private static final String TAG_CONNECTE = "recette-connectee";

    /// Le motif vit dans [MotifDeCas] : trois lecteurs de ces fichiers coexistent, et deux ont
    /// découvert séparément que certaines sessions cochent leurs puces.
    private static final Pattern CAS = MotifDeCas.CAS;

    /// Les cas déclarés par les sessions, par identifiant, associés au fichier qui les porte.
    private static Map<String, String> declares;

    /// Ceux d'entre eux que le script marque `*perceptif*`.
    private static Set<String> perceptifs;

    /// Ceux que le script déclare hors de portée d'un banc, vers le motif qu'il en donne (#4417).
    ///
    /// Un fait, pas une dette : le marqueur dit qu'il n'y aura JAMAIS de clip. C'est pourquoi il
    /// doit se justifier, et pourquoi il rougit le jour où un test se met à citer le cas.
    private static Map<String, String> horsDePortee;

    /// Ceux qui se filmeront, mais pas avant qu'un prérequis nommé existe, vers ce prérequis (#4458).
    ///
    /// Une dette DÉCLARÉE, là où [#horsDePortee] porte un fait définitif. Les deux se ressemblent à
    /// l'œil et ne se ressemblent pas du tout : l'un attend quelque chose de nommé, l'autre n'attend
    /// rien. Les confondre a rangé huit cas avec la carte SD réelle, et personne ne les a cherchés
    /// là (#4458).
    private static Map<String, String> prerequis;

    /// Ceux dont une ÉTAPE ne s'enregistre pas, vers ce que le carton descriptif dira à sa place.
    ///
    /// Le geste, lui, se filme ENTIER : c'est toute la différence avec [#horsDePortee], et c'est ce
    /// qui évite qu'une étape muette condamne un geste complet.
    private static Map<String, String> cartons;

    /// Le geste auquel un cas appartient, vers les cas qui le composent (#4417).
    ///
    /// Déclaré, et non déduit de l'étape de session. Une déduction ne laisserait rien à
    /// confronter : un clip qui prétend porter un geste ne pourrait pas être pris en défaut d'en
    /// oublier un cas.
    private static Map<String, Set<String>> gestes;

    /// Les cas cités par le code, et par quel test.
    private static Map<String, Set<String>> cites;

    /// Ceux d'entre eux que cite une classe portant [#TAG_CONNECTE].
    private static Set<String> casConnectes;

    /// Ce que les tests qui les citent prétendent prouver.
    private static Map<String, Set<Jugement>> jugements;

    /// Le tri qui en découle, calculé une fois.
    private static RepartitionDesCas tri;

    /// Pour chaque session balayée, le nombre de cas que [#CAS] y a lus - zéro compris.
    private static Map<String, Integer> casParFichier;

    /// Sur quoi le décompte porte, et sur quoi il ne porte pas.
    private static PerimetreDesSessions perimetre;

    /// Ce que chaque test déclare de son cas : où se lit le verdict, et ce que le clip laisse dehors.
    private static List<Citation> citations;

    /// Une citation de cas par un test, avec ce que ce test dit de sa portée (#4142).
    private record Citation(String test, String cas, Portee portee, String reserve) {}

    @BeforeAll
    static void lire() {
        declares = new LinkedHashMap<>();
        perceptifs = new LinkedHashSet<>();
        horsDePortee = new LinkedHashMap<>();
        prerequis = new LinkedHashMap<>();
        cartons = new LinkedHashMap<>();
        gestes = new LinkedHashMap<>();
        casParFichier = new LinkedHashMap<>();
        lireLesScripts();
        perimetre = PerimetreDesSessions.analyser(casParFichier, MUETTES_ADMISES.keySet());

        cites = new LinkedHashMap<>();
        casConnectes = new LinkedHashSet<>();
        jugements = new LinkedHashMap<>();
        citations = new ArrayList<>();
        lireLeCode();

        tri = RepartitionDesCas.repartir(declares.keySet(), perceptifs, jugements);

        // ⚠️ Retirée AVANT d'être réécrite, en deux gestes distincts. Le fichier survit d'un
        // lancement à l'autre : sans ce retrait, une dérivation qui cesserait de tourner
        // laisserait la liste d'hier en place, et son garde resterait vert dessus. Retirer
        // l'appel qui suit fait maintenant rougir, ce qu'on a vérifié.
        retirerLesClassesAFilmer();
        deposerLesClassesAFilmer();
        deposerLesSessionsAFilmer();
        deposerLesCasConnectes();
    }

    @Test
    @DisplayName("Tout cas cité par un test existe dans une session")
    void aucun_cas_cite_n_est_inconnu() {
        SoftAssertions verifs = new SoftAssertions();
        cites.forEach((cas, tests) -> verifs.assertThat(declares)
                .as(
                        "%s est cité par %s, mais aucune session ne le déclare. Un identifiant"
                                + " inventé ou renuméroté ne couvre rien : corrigez la citation, ou"
                                + " la session.",
                        cas, String.join(", ", tests))
                .containsKey(cas));
        verifs.assertAll();
    }

    @Test
    @DisplayName("Les cas que rien ne couvre sont listés, jamais tacites")
    void les_cas_non_couverts_sont_nommes() {
        // Ce test ne réclame PAS la couverture : il l'affiche. Il n'échoue que si le garde lui-même
        // ne lit plus rien - sans quoi il rendrait vert sur un dépôt sans aucune session.
        assertThat(declares)
                .as("Aucun cas de recette n'a été lu sous %s : le garde ne garde plus rien.", SESSIONS)
                .isNotEmpty();

        System.out.printf(
                "%nCorrespondance recette (%s) : %d cas déclarés, %d assertés, %d perceptifs, %d non couverts.%n",
                perimetre.assiette(),
                declares.size(),
                tri.assertes().size(),
                tri.perceptifs().size(),
                tri.nonCouverts().size());

        // ⚠️ Les muettes s'affichent AVANT les cas. Le lecteur qui s'arrête à la première ligne doit
        // déjà savoir ce que le décompte ne couvre pas ; les mettre en pied de sortie reviendrait à
        // les réserver à qui lit tout, c'est-à-dire à personne.
        perimetre
                .muettes()
                .forEach(session -> System.out.printf(
                        "  hors périmètre · %s · %s%n",
                        session, MUETTES_ADMISES.getOrDefault(session, "aucun cas au format lu, et rien ne l'admet")));

        tri.perceptifs()
                .forEach(cas -> System.out.printf(
                        "  perceptif   · %s · %s · %s%n",
                        cas,
                        declares.get(cas),
                        cites.containsKey(cas) ? "joué par " + joindre(cites.get(cas)) : "à jouer"));
        tri.nonCouverts().forEach(cas -> System.out.printf("  non couvert · %s · %s%n", cas, declares.get(cas)));
    }

    @Test
    @DisplayName("#4417 : un cas hors de portée le reste, ou son marqueur tombe")
    void un_cas_hors_de_portee_n_est_pas_cite() {
        SoftAssertions verifs = new SoftAssertions();
        horsDePortee.forEach((cas, motif) -> verifs.assertThat(cites.containsKey(cas))
                .as(
                        "%s est marqué *hors-portée: %s* dans %s, et pourtant %s le cite. Le marqueur"
                                + " porte un FAIT - « il n'y aura jamais de clip » - et il vient d'être"
                                + " démenti : le retirer, plutôt que de laisser une déclaration périmée"
                                + " faire croire à un arbitrage qui n'a plus cours.",
                        cas, motif, declares.get(cas), cites.containsKey(cas) ? joindre(cites.get(cas)) : "?")
                .isFalse());
        verifs.assertAll();
    }

    @Test
    @DisplayName("#4417 : un marqueur qui porte un fait porte aussi son motif")
    void un_marqueur_de_fait_se_justifie() {
        SoftAssertions verifs = new SoftAssertions();
        horsDePortee.forEach((cas, motif) -> verifs.assertThat(motif)
                .as(
                        "%s est marqué *hors-portée* sans motif dans %s. Un marqueur sans raison est un"
                                + " tapis : il retire le cas du décompte sans que personne puisse dire"
                                + " si c'est encore vrai.",
                        cas, declares.get(cas))
                .isNotBlank());
        cartons.forEach((cas, texte) -> verifs.assertThat(texte)
                .as(
                        "%s est marqué *carton* sans dire ce que le carton annoncera, dans %s. Le"
                                + " carton REMPLACE une étape à l'écran : son texte est ce que le"
                                + " spectateur lira à la place.",
                        cas, declares.get(cas))
                .isNotBlank());
        prerequis.forEach((cas, attendu) -> verifs.assertThat(attendu)
                .as(
                        "%s est marqué *prérequis* sans nommer ce qu'il attend, dans %s. Un prérequis"
                                + " anonyme ne se vérifie pas : personne ne peut dire s'il est arrivé,"
                                + " donc le marqueur ne tombera jamais et le cas dormira.",
                        cas, declares.get(cas))
                .isNotBlank());
        verifs.assertAll();
    }

    @Test
    @DisplayName("#4458 : un cas attend un prérequis OU n'aura jamais de clip, jamais les deux")
    void prerequis_et_hors_portee_s_excluent() {
        SoftAssertions verifs = new SoftAssertions();
        prerequis.forEach((cas, attendu) -> verifs.assertThat(horsDePortee)
                .as(
                        "%s est à la fois marqué *prérequis: %s* et *hors-portée: %s* dans %s. Les deux"
                                + " se contredisent : l'un dit que le cas attend quelque chose de nommé,"
                                + " l'autre qu'il n'y aura jamais de clip. Choisir, sans quoi le lecteur"
                                + " prend celui qui l'arrange.",
                        cas, attendu, horsDePortee.get(cas), declares.get(cas))
                .doesNotContainKey(cas));
        verifs.assertAll();
    }

    @Test
    @DisplayName("#4458 : un cas qui attendait est couvert, ou son marqueur tombe")
    void un_cas_a_prerequis_couvert_perd_son_marqueur() {
        SoftAssertions verifs = new SoftAssertions();
        prerequis.forEach((cas, attendu) -> verifs.assertThat(cites.containsKey(cas))
                .as(
                        "%s est marqué *prérequis: %s* dans %s, et pourtant %s le cite. Le prérequis est"
                                + " donc arrivé : retirer le marqueur, plutôt que de laisser une dette"
                                + " déclarée compter un cas qui est couvert.",
                        cas, attendu, declares.get(cas), cites.containsKey(cas) ? joindre(cites.get(cas)) : "?")
                .isFalse());
        verifs.assertAll();
    }

    @Test
    @DisplayName("#4458 : le marqueur de prérequis sert, ou le garde le dit")
    void le_marqueur_de_prerequis_n_est_pas_lettre_morte() {
        // Article A3 : un dispositif qui peut ne rien vérifier le dit. Les trois gardes ci-dessus
        // seraient VERTS sur un corpus où plus personne n'écrit *prérequis*, et ce vert-là ne
        // vaudrait rien. Celui-ci rougit le jour où le mot disparaît, pour qu'on le retire du
        // vocabulaire au lieu de le laisser dormir.
        assertThat(prerequis)
                .as("Aucun cas ne porte *prérequis* : soit le mot ne sert plus et il faut le retirer"
                        + " de MotifDeCas avec ses gardes, soit une réécriture vient de l'effacer"
                        + " par mégarde. Les trois gardes de #4458 ne vérifient plus rien.")
                .isNotEmpty();
    }

    @Test
    @DisplayName("#4417 : les gestes sont comptés, et ceux qu'un clip coupe sont nommés")
    void les_gestes_sont_comptes() {
        // Ce test ne réclame PAS qu'un geste soit entier : il l'affiche, comme son voisin le fait
        // de la couverture. Un geste à moitié filmé est une DETTE, et la dette se lit.
        //
        // Le premier contact avec de vraies données l'a montré : le geste « connexion-longue » compte
        // cinq cas, et le clip qui existe en cite trois. Les deux manquants SE PRODUISENT pendant ce
        // clip - la barre avance, l'estimation paraît - mais rien ne les asserte. Les citer sans les
        // asserter serait un mensonge, et faire rougir ici reviendrait à l'exiger.
        //
        // Il n'échoue que si le garde lui-même ne lit plus rien, sans quoi il rendrait vert sur un
        // dépôt sans aucun geste déclaré.
        assertThat(declares)
                .as("Aucun cas de recette n'a été lu sous %s : le garde ne garde plus rien.", SESSIONS)
                .isNotEmpty();

        Map<String, Set<String>> coupes = new LinkedHashMap<>();
        int entiers = 0;
        for (Map.Entry<String, Set<String>> geste : gestes.entrySet()) {
            Set<String> manquants = geste.getValue().stream()
                    .filter(cas -> !cites.containsKey(cas))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (manquants.isEmpty()) {
                entiers++;
            } else if (manquants.size() < geste.getValue().size()) {
                coupes.put(geste.getKey(), manquants);
            }
        }

        System.out.printf(
                "%nGestes déclarés : %d, dont %d filmés entiers, %d coupés, %d sans aucun clip.%n",
                gestes.size(), entiers, coupes.size(), gestes.size() - entiers - coupes.size());
        coupes.forEach((geste, manquants) -> System.out.printf(
                "  geste coupé · %s · un clip en porte une partie, il manque %s%n", geste, joindre(manquants)));

        // Ce que le décompte apprend, et qui décide du palier suivant : combien de CLIPS il faudrait,
        // au lieu de combien de cas.
        long casGroupes = gestes.values().stream().mapToLong(Set::size).sum();
        System.out.printf(
                "  %d cas se regroupent en %d gestes, soit %.1f cas par clip au lieu de 1.%n",
                casGroupes, gestes.size(), gestes.isEmpty() ? 0 : (double) casGroupes / gestes.size());
    }

    @Test
    @DisplayName("#4142 : un cas hors application dit ce que son clip ne prouve pas, les autres se taisent")
    void la_reserve_accompagne_la_portee() {
        assertThat(citations)
                .as("Aucune citation n'a été lue : le garde ne garde plus rien.")
                .isNotEmpty();

        SoftAssertions verifs = new SoftAssertions();
        for (Citation citation : citations) {
            if (citation.portee() == Portee.HORS_APPLICATION) {
                verifs.assertThat(citation.reserve())
                        .as(
                                "%s cite %s comme un cas dont le verdict est HORS de l'application, sans"
                                        + " dire ce que son clip ne prouve pas. Un clip bouchonné y montre"
                                        + " un écran convaincant et creux : la phrase qui le borne n'est"
                                        + " pas un ornement, c'est ce qui empêche de le croire sur parole.",
                                citation.test(), citation.cas())
                        .isNotBlank();
            } else {
                verifs.assertThat(citation.reserve())
                        .as(
                                "%s porte une réserve sur %s, dont le verdict se lit à l'écran. Une page"
                                        + " qui met une réserve partout n'en fait lire aucune : ou bien la"
                                        + " portée est fausse, ou bien la phrase est du bruit.",
                                citation.test(), citation.cas())
                        .isBlank();
            }
        }
        verifs.assertAll();

        long horsApplication = citations.stream()
                .filter(citation -> citation.portee() == Portee.HORS_APPLICATION)
                .count();
        System.out.printf(
                "%nPortée des cas cités : %d à l'écran, %d hors application.%n",
                citations.size() - horsApplication, horsApplication);
    }

    @Test
    @DisplayName("#4142 : la réserve est sur la page, et non seulement dans le code")
    void la_reserve_atteint_celui_qui_regarde() {
        SoftAssertions verifs = new SoftAssertions();
        for (Citation citation : citations) {
            if (citation.portee() != Portee.HORS_APPLICATION) {
                continue;
            }
            verifs.assertThat(sectionDesPages(citation))
                    .as(
                            "%s est un cas dont le verdict est hors de l'application, et sa réserve"
                                    + " n'apparaît nulle part sur les pages de clips. Elle vit alors dans"
                                    + " le code, que personne ne lit en regardant un clip : la page reste"
                                    + " un écran convaincant, sans la phrase qui le borne.",
                            citation.cas())
                    .contains(citation.reserve());
        }
        verifs.assertAll();
    }

    /// Ce que les deux pages de clips écrivent **sous la section de CE clip**, jusqu'à la suivante.
    ///
    /// ⚠️ On lit la section, pas la page entière : une réserve écrite ailleurs sur la page ne borne pas
    /// le clip qu'on regarde, et le garde la compterait pourtant.
    ///
    /// ⚠️ Et on vise la section du **test**, pas celle du cas. Un cas couvert par plusieurs tests a
    /// plusieurs sections - `S1-04` en a trois, une par geste de la modale - et chercher la première
    /// venue faisait lire la réserve d'un autre clip. Le garde refusait alors une réserve pourtant
    /// posée, ce qui aurait fait la recopier partout (#4158).
    private static String sectionDesPages(Citation citation) {
        String methode = citation.test().substring(citation.test().indexOf('.') + 1);
        String titre = "### " + citation.cas() + " · `" + methode + "`";
        StringBuilder trouve = new StringBuilder();
        for (Path page : List.of(PAGE_PERCEPTIFS, PAGE_ASSERTES)) {
            if (!Files.isRegularFile(page)) {
                continue;
            }
            String texte = lire(page);
            int debut = texte.indexOf(titre);
            if (debut < 0) {
                continue;
            }
            int fin = texte.indexOf("\n### ", debut + 1);
            int finSection = texte.indexOf("\n## ", debut + 1);
            if (finSection >= 0 && (fin < 0 || finSection < fin)) {
                fin = finSection;
            }
            trouve.append(fin < 0 ? texte.substring(debut) : texte, debut, fin < 0 ? texte.length() : fin);
        }
        return trouve.toString();
    }

    @Test
    @DisplayName("Le garde nomme les sessions qu'il ne lit pas")
    void le_perimetre_ne_se_tait_sur_aucune_session() {
        SoftAssertions verifs = new SoftAssertions();

        verifs.assertThat(perimetre.silencesNonDeclares())
                .as(
                        "Ces sessions existent sous %s et ce garde n'en lit aucun cas, sans que rien"
                                + " ne le dise. Un décompte qui tait son assiette se lit plus large"
                                + " qu'il ne porte. Ajoutez-les à MUETTES_ADMISES avec leur raison,"
                                + " ou donnez-leur le format `- **Sxx-NN** ·`.",
                        SESSIONS)
                .isEmpty();

        verifs.assertThat(perimetre.admissionsPerimees())
                .as("Ces sessions sont admises muettes et ne le sont plus - elles rendent des cas,"
                        + " ou elles ont disparu. Retirez-les de MUETTES_ADMISES : une liste"
                        + " d'exceptions qu'on n'élague pas redevient la prose qui dérive.")
                .isEmpty();

        verifs.assertAll();
    }

    @Test
    @DisplayName("Le script et le code disent la même chose du juge")
    void le_script_et_le_code_s_accordent_sur_le_juge() {
        // Le devoir neuf de #3764, et le seul des trois qui puisse rougir sur le contenu du dépôt.
        // Les deux sources se recoupent : c'est ce recoupement qui les tient l'une par l'autre.
        String explications = tri.desaccords().entrySet().stream()
                .map(desaccord -> explication(desaccord.getKey(), desaccord.getValue()))
                .collect(Collectors.joining(System.lineSeparator() + "  "));

        assertThat(tri.desaccords())
                .as("Le script et le code ne disent pas la même chose du juge :%n  %s", explications)
                .isEmpty();
    }

    @Test
    @DisplayName("La liste des classes à filmer est déposée, et elle exclut les exemples")
    void la_liste_des_classes_a_filmer_est_deposee() throws IOException {
        // Le script de contact (#3835) filme ces classes-là et pas d'autres. Une liste vide ou
        // absente ferait tourner une séance qui ne montre rien, sans rien dire.
        assertThat(CLASSES_A_FILMER)
                .as("la liste n a pas ete deposee par CETTE seance : le script de contact"
                        + " filmerait le vide, ou pire, la liste d une seance precedente")
                .exists();

        List<String> classes = Files.readAllLines(CLASSES_A_FILMER);
        assertThat(classes)
                .as("aucune classe citante : le balayage du classpath ne trouve plus rien")
                .isNotEmpty();
        assertThat(classes)
                .as(
                        "les exemples de %s imitent un test sans rien couvrir : les filmer donnerait des"
                                + " extraits d'un décor, pas du produit",
                        FixtureDeRecette.class.getSimpleName())
                .doesNotContain("ReperesDeSeanceTest");
        assertThat(classes).doesNotHaveDuplicates().isSorted();
        assertThat(classes)
                .as("la liste déposée doit être celle de CETTE séance, pas celle d'une précédente")
                .containsExactlyElementsOf(classesCitantes());
    }

    /// Le garde du rangement PAR SESSION, dont dépend le tournage d'une seule session (#4163).
    ///
    /// ⚠️ Les deux assertions ne disent pas la même chose, et il faut les deux. La première dit
    /// qu'aucune classe ne se perd en chemin : un tournage session par session doit couvrir
    /// exactement ce qu'un tournage complet couvre, sans quoi des cas cesseraient d'être filmés
    /// sans que personne ne l'ait décidé. La seconde dit que le compte des cas est juste, et c'est
    /// lui que le tournage compare à ce qu'il a produit - un compte faux rendrait le garde du
    /// tournage vert sur un tournage incomplet.
    @Test
    @DisplayName("Le rangement par session couvre les mêmes classes, et compte ses cas juste")
    void le_rangement_par_session_couvre_les_memes_classes() throws IOException {
        assertThat(SESSIONS_A_FILMER).exists();
        List<String> lignes = Files.readAllLines(SESSIONS_A_FILMER);

        Set<String> vues = new TreeSet<>();
        int casComptes = 0;
        for (String ligne : lignes) {
            String[] colonnes = ligne.split("\t", -1);
            assertThat(colonnes).as("session, nombre de cas, classes").hasSize(3);
            casComptes += Integer.parseInt(colonnes[1]);
            vues.addAll(List.of(colonnes[2].split(",")));
        }

        assertThat(vues)
                .as("une session par session doit couvrir exactement ce qu'un tournage complet couvre")
                .containsExactlyInAnyOrderElementsOf(classesCitantes());
        assertThat(casComptes)
                .as("chaque cas cité appartient à une session et une seule")
                .isEqualTo(cites.size());
    }

    // ----------------------------------------------------------------------------------------

    /// Les classes qui citent au moins un cas, triées.
    private static Set<String> classesCitantes() {
        Set<String> classes = new TreeSet<>();
        cites.values().forEach(tests -> tests.forEach(test -> classes.add(test.substring(0, test.indexOf('.')))));
        return classes;
    }

    /// Retire les listes de la séance précédente, pour que la suivante ne puisse pas les relire.
    private static void retirerLesClassesAFilmer() {
        try {
            Files.deleteIfExists(CLASSES_A_FILMER);
            Files.deleteIfExists(SESSIONS_A_FILMER);
        } catch (IOException e) {
            throw new UncheckedIOException("Liste des classes à filmer impossible à retirer", e);
        }
    }

    /// Les classes à filmer pour chaque session, avec le nombre de cas que la session fait jouer.
    ///
    /// La session se lit dans l'identifiant du cas : `S6-27` appartient à `S6`. Filmer une session,
    /// c'est donc filmer les classes qui citent au moins un de ses cas - et une classe qui en cite
    /// de deux sessions paraît dans les deux, ce qui est correct : elle sera tournée deux fois, et
    /// chaque tournage ne gardera que ce qu'il est venu chercher.
    private static SortedMap<String, SortedSet<String>> classesParSession() {
        SortedMap<String, SortedSet<String>> parSession = new TreeMap<>();
        cites.forEach((cas, tests) -> {
            String session = sessionDe(cas);
            tests.forEach(test -> parSession
                    .computeIfAbsent(session, cle -> new TreeSet<>())
                    .add(test.substring(0, test.indexOf('.'))));
        });
        return parSession;
    }

    /// Les cas CITÉS de chaque session. Les cas déclarés mais non couverts n'ont rien à filmer.
    private static SortedMap<String, SortedSet<String>> casCitesParSession() {
        SortedMap<String, SortedSet<String>> parSession = new TreeMap<>();
        cites.keySet()
                .forEach(cas -> parSession
                        .computeIfAbsent(sessionDe(cas), cle -> new TreeSet<>())
                        .add(cas));
        return parSession;
    }

    private static String sessionDe(String cas) {
        int tiret = cas.indexOf('-');
        return tiret < 0 ? cas : cas.substring(0, tiret);
    }

    /// Dépose, par session : le nombre de cas cités, puis les classes qui les jouent.
    ///
    /// Le nombre de cas est déposé AVEC les classes parce que c'est lui qui permet au tournage de
    /// dire s'il a rendu ce qu'il devait rendre. Sans lui, un tournage de session ne pourrait
    /// vérifier que « des clips sont sortis », ce qui est le genre de garde qui ne garde rien.
    private static void deposerLesSessionsAFilmer() {
        SortedMap<String, SortedSet<String>> classes = classesParSession();
        SortedMap<String, SortedSet<String>> cas = casCitesParSession();
        List<String> lignes = new ArrayList<>();
        classes.forEach((session, noms) -> lignes.add(
                session + "\t" + cas.getOrDefault(session, new TreeSet<>()).size() + "\t" + String.join(",", noms)));
        try {
            Files.createDirectories(SESSIONS_A_FILMER.getParent());
            Files.write(SESSIONS_A_FILMER, lignes);
        } catch (IOException e) {
            throw new UncheckedIOException("Liste des sessions à filmer impossible à écrire", e);
        }
    }

    /// Dépose le nombre de cas que cite le corpus connecté, et eux seuls (#4326).
    ///
    /// Un seul nombre, sur une ligne : c'est tout ce dont le tournage a besoin pour dire s'il a rendu
    /// ce qu'il devait rendre. Zéro est une valeur légitime - aucun scénario connecté n'existe encore
    /// dans certaines branches - et le tournage la lit comme telle.
    private static void deposerLesCasConnectes() {
        try {
            Files.createDirectories(CAS_CONNECTES.getParent());
            Files.write(CAS_CONNECTES, List.of(String.valueOf(casConnectes.size())));
        } catch (IOException e) {
            throw new UncheckedIOException("Compte des cas connectés impossible à écrire", e);
        }
    }

    /// Dépose les classes qui citent au moins un cas, une par ligne, triées.
    private static void deposerLesClassesAFilmer() {
        try {
            Files.createDirectories(CLASSES_A_FILMER.getParent());
            Files.write(CLASSES_A_FILMER, classesCitantes());
        } catch (IOException e) {
            throw new UncheckedIOException("Liste des classes à filmer impossible à écrire", e);
        }
    }

    private static String explication(String cas, Jugement ceQueDitLeCode) {
        String tests = joindre(cites.getOrDefault(cas, Set.of()));
        // ⚠️ Les parenthèses ne sont pas décoratives : sans elles, `.formatted` ne s'applique qu'au
        // DERNIER fragment de la concaténation, et le message part avec ses `%s` intacts. Le témoin
        // du chantier l'a montré avant qu'un lecteur ait à le subir.
        if (ceQueDitLeCode == Jugement.AUTOMATIQUE) {
            return ("%s est marqué *perceptif* dans %s, mais %s prétend l'asserter. Ou bien le script"
                            + " a tort et le cas est automatisable - retirez la marque -, ou bien le"
                            + " test surestime ce qu'il prouve.")
                    .formatted(cas, declares.get(cas), tests);
        }
        return ("%s se déclare jugé par un humain dans %s, mais %s ne le marque pas *perceptif*. Ou"
                        + " bien le cas relève de l'oeil - marquez-le -, ou bien ce scénario doit"
                        + " asserter.")
                .formatted(cas, tests, declares.get(cas));
    }

    private static String joindre(Set<String> noms) {
        return String.join(", ", noms);
    }

    private static void lireLesScripts() {
        if (!Files.isDirectory(SESSIONS)) {
            return;
        }
        try (Stream<Path> fichiers = Files.list(SESSIONS)) {
            fichiers.filter(f -> f.getFileName().toString().endsWith(".md"))
                    .sorted()
                    .forEach(f -> {
                        String nom = f.getFileName().toString();
                        // ⚠️ Posé à zéro AVANT de lire, pour que les sessions dont la regex ne tire
                        // rien figurent quand même au périmètre. Ne compter que ce qui parle rendrait
                        // le silence indiscernable de l'absence : le défaut même de #3884.
                        casParFichier.put(nom, 0);
                        Matcher m = CAS.matcher(lire(f));
                        while (m.find()) {
                            declares.putIfAbsent(m.group(1), nom);
                            casParFichier.merge(nom, 1, Integer::sum);
                            String cas = m.group(1);
                            Map<String, String> marques = MotifDeCas.marqueurs(m.group(2));
                            if (marques.containsKey(MotifDeCas.PERCEPTIF)) {
                                perceptifs.add(cas);
                            }
                            if (marques.containsKey(MotifDeCas.HORS_PORTEE)) {
                                horsDePortee.put(cas, marques.get(MotifDeCas.HORS_PORTEE));
                            }
                            if (marques.containsKey(MotifDeCas.PREREQUIS)) {
                                prerequis.put(cas, marques.get(MotifDeCas.PREREQUIS));
                            }
                            if (marques.containsKey(MotifDeCas.CARTON)) {
                                cartons.put(cas, marques.get(MotifDeCas.CARTON));
                            }
                            String geste = marques.get(MotifDeCas.GESTE);
                            if (geste != null && !geste.isBlank()) {
                                gestes.computeIfAbsent(geste, g -> new LinkedHashSet<>())
                                        .add(cas);
                            }
                        }
                    });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void lireLeCode() {
        JavaClasses classes = new ClassFileImporter().importPackages("fr.univ_amu.iut");
        classes.forEach(classe -> {
            // Les exemples qui éprouvent l'outillage citent de vrais identifiants sans rien couvrir :
            // les compter gonflerait l'index de tests qui n'exercent pas le produit. Voir
            // FixtureDeRecette, où l'exclusion est expliquée et rendue portante.
            if (classe.isAnnotatedWith(FixtureDeRecette.class)) {
                return;
            }
            // ⚠️ Lu sur la CLASSE, parce que c'est là que vit `@Tag` : surefire sélectionne par
            // classe, et un tag posé sur une méthode ne dirait pas ce que le tournage jouera.
            boolean connectee = classe.tryGetAnnotationOfType(Tag.class)
                    .map(tag -> TAG_CONNECTE.equals(tag.value()))
                    .orElse(false);
            classe.getMethods()
                    .forEach(methode -> methode.tryGetAnnotationOfType(CasDeRecette.class)
                            .ifPresent(annotation -> {
                                String nom = classe.getSimpleName() + "." + methode.getName();
                                for (String id : List.of(annotation.value())) {
                                    if (connectee) {
                                        casConnectes.add(id);
                                    }
                                    cites.computeIfAbsent(id, k -> new LinkedHashSet<>())
                                            .add(nom);
                                    jugements
                                            .computeIfAbsent(id, k -> EnumSet.noneOf(Jugement.class))
                                            .add(annotation.jugement());
                                    citations.add(new Citation(nom, id, annotation.portee(), annotation.reserve()));
                                }
                            }));
        });
    }

    private static String lire(Path fichier) {
        try {
            return Files.readString(fichier);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
