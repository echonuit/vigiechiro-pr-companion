package fr.univ_amu.iut.maj.api;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.maj.model.VersionDisponible;
import java.net.URI;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// L'adaptateur réseau : le **seul** endroit du chantier qui sort de la machine.
///
/// Son contrat tient en une phrase - **toute défaillance rend `Optional.empty()`** - et c'est
/// précisément ce qu'on ne peut pas vérifier en appelant GitHub, puisqu'il faudrait provoquer une
/// panne à volonté.
///
/// Le dépôt ne peut pas non plus monter un serveur local : `jdk.httpserver` n'est pas lisible depuis
/// ce module (contrainte JPMS connue). On exerce donc l'**interprétation de la réponse** - code puis
/// corps - directement, plus le **hors-ligne** qui ne demande aucun serveur. Depuis #2193, cela
/// couvre aussi l'aiguillage sur le code HTTP, qui échappait au test.
class DerniereVersionGitHubTest {

    private final DerniereVersionGitHub adaptateur = new DerniereVersionGitHub();

    @Test
    @DisplayName("une réponse conforme donne le numéro et l'adresse de la page")
    void reponseConforme() {
        Optional<VersionDisponible> lue = adaptateur.interpreter(200, """
                {"tag_name": "v2.26.0",
                 "html_url": "https://github.com/exemple/releases/tag/v2.26.0"}""");

        assertThat(lue).isPresent();
        assertThat(lue.orElseThrow().numero()).hasToString("2.26.0");
        assertThat(lue.orElseThrow().adresse())
                .as("l'annonce ne sert à rien si elle ne dit pas où aller")
                .isEqualTo("https://github.com/exemple/releases/tag/v2.26.0");
    }

    @Test
    @DisplayName("une réponse sans les champs attendus se tait")
    void reponseIncompleteSeTait() {
        assertThat(adaptateur.interpreter(200, "{\"message\": \"Not Found\"}")).isEmpty();
        assertThat(adaptateur.interpreter(200, "{\"tag_name\": \"v2.26.0\"}"))
                .as("un numéro sans adresse ne permet pas d'agir")
                .isEmpty();
    }

    @Test
    @DisplayName("un tag qui n'est pas un numéro se tait")
    void tagNonNumeriqueSeTait() {
        assertThat(adaptateur.interpreter(200, "{\"tag_name\": \"nightly\", \"html_url\": \"https://exemple\"}"))
                .isEmpty();
        assertThat(adaptateur.interpreter(200, "{\"tag_name\": \"v2.26.0-rc.1\", \"html_url\": \"https://exemple\"}"))
                .as("une pré-version ne se propose pas à un naturaliste")
                .isEmpty();
    }

    @Test
    @DisplayName("un code autre que 200 se tait, quel qu'il soit")
    void codeNonSuccesSeTait() {
        // Le corps est volontairement VALIDE : c'est le code seul qui doit faire renoncer. Sans quoi
        // le test passerait pour la mauvaise raison.
        String corpsValide = "{\"tag_name\": \"v2.26.0\", \"html_url\": \"https://exemple\"}";

        // 403 : la limite de débit anonyme de GitHub, le cas le plus probable en usage réel.
        assertThat(adaptateur.interpreter(403, corpsValide)).isEmpty();
        // 404 : dépôt renommé ou release retirée.
        assertThat(adaptateur.interpreter(404, corpsValide)).isEmpty();
        // 500 : panne du service.
        assertThat(adaptateur.interpreter(500, corpsValide)).isEmpty();

        // Contre-épreuve : le MÊME corps passe en 200. Sans elle, les trois cas ci-dessus seraient
        // verts même si l'analyse était cassée.
        assertThat(adaptateur.interpreter(200, corpsValide))
                .as("le corps est bon : seul le code devait faire renoncer")
                .isPresent();
    }

    @Test
    @DisplayName("hors ligne : rien, et surtout aucune exception")
    void horsLigneSeTait() {
        // Port fermé : l'équivalent testable d'une machine sans réseau. C'est LE cas qui ne doit
        // jamais remonter à l'utilisateur, puisqu'il survient au démarrage.
        DerniereVersionGitHub horsLigne = new DerniereVersionGitHub(URI.create("http://127.0.0.1:1/"));

        assertThat(horsLigne.consulter()).isEmpty();
    }
}
