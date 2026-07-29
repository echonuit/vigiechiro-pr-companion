package fr.univ_amu.iut.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/// Le **minion de PIT** reçoit les mêmes propriétés système que la JVM forkée par Surefire.
///
/// **Pourquoi ce garde-fou existe.** PIT hérite de l'`argLine` de Surefire mais **pas** de ses
/// `systemPropertyVariables` : le profil `mutation` du `pom.xml` doit donc les reproduire, une à une,
/// dans ses `jvmArgs`. Cette duplication est une contrainte de l'outil, et une duplication se
/// désynchronise.
///
/// Elle l'a fait. Le profil en reproduisait **quatre sur six** : les deux butoirs d'interblocage TestFX
/// (#2120) manquaient. Conséquence, tout test TestFX exécuté par un minion retombait sur les défauts de
/// TestFX (30 s et 60 s) - et c'est **sous PIT** que la machine est la plus chargée, une JVM par cœur.
/// Le pire endroit pour un butoir calibré pour une JVM seule.
///
/// Rien ne l'a dit pendant des semaines. [ButoirsTestFxTest] l'aurait dit - c'est exactement son rôle -
/// mais seulement **exécuté par un minion**, c'est-à-dire lors de la mesure hebdomadaire de mutation,
/// laquelle échouait par ailleurs pour une autre raison. Le premier passage réussi de PIT sur le dépôt
/// entier a donc échoué sur une **suite rouge**, pas sur des mutants.
///
/// Ce test-ci parle au moment du `build`, sans attendre lundi.
///
/// **Il lit le `pom.xml` en XML, pas en texte.** Une première version le balayait à coups d'expressions
/// régulières et confondait les blocs : elle ramassait aussi les `systemPropertyVariables` du profil
/// `api-live` (jeton, URL, autorisation d'écriture), qui n'ont rien à faire dans un minion et ne sont
/// posées que sous `-Papi-live`. Un garde-fou qui réclame la mauvaise chose se fait désactiver.
class ProprietesMinionPitTest {

    /// Surefire s'exécute depuis la racine du projet : le chemin est relatif à elle.
    private static final Path POM = Path.of("pom.xml");

    @Test
    @DisplayName("Le profil `mutation` transmet au minion TOUTES les propriétés de Surefire")
    void le_minion_recoit_les_memes_proprietes_que_surefire() {
        Element projet = racine();
        Set<String> surefire = proprietesSurefireDeBase(projet);
        Set<String> minion = proprietesDuMinion(projet);

        assertThat(surefire)
                .as("aucune propriété lue sous <build>/maven-surefire-plugin : la lecture ne trouve plus"
                        + " sa configuration, donc ce test ne garde plus rien")
                .isNotEmpty();
        assertThat(minion)
                .as("aucun -D lu dans les <jvmArgs> du profil `mutation` : la lecture ne trouve plus"
                        + " sa configuration")
                .isNotEmpty();

        assertThat(minion)
                .as("Ces propriétés sont posées sur la JVM de Surefire mais PAS sur le minion de PIT."
                        + " Un test qui en dépend passe au build et échoue à la mesure hebdomadaire —"
                        + " ou pire, passe partout en s'exécutant dans de mauvaises conditions."
                        + " Reprenez-les dans <jvmArgs> du profil `mutation`.")
                .containsAll(surefire);
    }

    /// Les propriétés posées par la configuration Surefire **de base** : celle de `<build>`, qui
    /// s'applique à toute exécution. Les blocs des profils (`api-live`) en sont exclus : ils ne valent
    /// que sous leur drapeau, et le minion ne les voit jamais.
    private static Set<String> proprietesSurefireDeBase(Element projet) {
        return enfant(projet, "build")
                .flatMap(build -> plugin(build, "maven-surefire-plugin"))
                .flatMap(plugin -> enfant(plugin, "configuration"))
                .flatMap(config -> enfant(config, "systemPropertyVariables"))
                .map(ProprietesMinionPitTest::nomsDesEnfants)
                .orElseGet(Set::of);
    }

    /// Les propriétés transmises au minion : les `<jvmArg>-Dnom=…` du greffon PIT, dans le profil
    /// `mutation`.
    private static Set<String> proprietesDuMinion(Element projet) {
        return profil(projet, "mutation")
                .flatMap(profil -> enfant(profil, "build"))
                .flatMap(build -> plugin(build, "pitest-maven"))
                .flatMap(plugin -> enfant(plugin, "configuration"))
                .flatMap(config -> enfant(config, "jvmArgs"))
                .map(ProprietesMinionPitTest::nomsDesProprietesJvm)
                .orElseGet(Set::of);
    }

    private static Set<String> nomsDesEnfants(Element parent) {
        Set<String> noms = new LinkedHashSet<>();
        NodeList enfants = parent.getChildNodes();
        for (int i = 0; i < enfants.getLength(); i++) {
            if (enfants.item(i) instanceof Element element) {
                noms.add(element.getTagName());
            }
        }
        return noms;
    }

    /// `-Dnom=valeur` → `nom`. Un `<jvmArg>` sans `-D` (une option de JVM) est ignoré : il n'a pas de
    /// pendant côté Surefire.
    private static Set<String> nomsDesProprietesJvm(Element jvmArgs) {
        Set<String> noms = new LinkedHashSet<>();
        NodeList args = jvmArgs.getElementsByTagName("jvmArg");
        for (int i = 0; i < args.getLength(); i++) {
            String arg = args.item(i).getTextContent().trim();
            if (arg.startsWith("-D") && arg.contains("=")) {
                noms.add(arg.substring(2, arg.indexOf('=')));
            }
        }
        return noms;
    }

    private static Optional<Element> plugin(Element parent, String artifactId) {
        return enfant(parent, "plugins")
                .flatMap(plugins -> enfantsNommes(plugins, "plugin").stream()
                        .filter(plugin -> enfant(plugin, "artifactId")
                                .map(id -> artifactId.equals(id.getTextContent().trim()))
                                .orElse(false))
                        .findFirst());
    }

    private static Optional<Element> profil(Element projet, String id) {
        return enfant(projet, "profiles")
                .flatMap(profiles -> enfantsNommes(profiles, "profile").stream()
                        .filter(profil -> enfant(profil, "id")
                                .map(valeur -> id.equals(valeur.getTextContent().trim()))
                                .orElse(false))
                        .findFirst());
    }

    /// Enfant **direct** portant ce nom : `getElementsByTagName` descendrait dans tout le sous-arbre et
    /// confondrait, par exemple, la configuration d'un greffon avec celle d'une de ses exécutions.
    private static Optional<Element> enfant(Element parent, String nom) {
        return enfantsNommes(parent, nom).stream().findFirst();
    }

    private static java.util.List<Element> enfantsNommes(Element parent, String nom) {
        java.util.List<Element> trouves = new java.util.ArrayList<>();
        NodeList enfants = parent.getChildNodes();
        for (int i = 0; i < enfants.getLength(); i++) {
            Node noeud = enfants.item(i);
            if (noeud instanceof Element element && nom.equals(element.getTagName())) {
                trouves.add(element);
            }
        }
        return trouves;
    }

    private static Element racine() {
        try {
            var fabrique = DocumentBuilderFactory.newInstance();
            fabrique.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return fabrique.newDocumentBuilder().parse(POM.toFile()).getDocumentElement();
        } catch (Exception illisible) {
            throw new IllegalStateException("pom.xml illisible : " + POM, illisible);
        }
    }
}
