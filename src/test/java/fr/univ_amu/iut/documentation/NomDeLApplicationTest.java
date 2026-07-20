package fr.univ_amu.iut.documentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Garde-fou de **nom** (#2147) : la documentation utilisateur appelle l'application par son nom, et
/// pas par celui de la plateforme.
///
/// ## Pourquoi ce test existe
///
/// L'application s'appelle **VigieChiro PR Companion** — c'est ce que son bandeau affiche. La
/// plateforme nationale à laquelle elle se connecte s'appelle **Vigie-Chiro**. La documentation
/// écrivait « VigieChiro » pour les deux, jusque dans le `site_name` du site produit.
///
/// La confusion n'est pas cosmétique : elle produit des phrases qui ne veulent rien dire. « Déposer
/// sur VigieChiro », dans une page qui vient d'appeler l'application « VigieChiro », ne désigne plus
/// rien de précis.
///
/// ## La frontière, et pourquoi elle est mécanique
///
/// **Le tiret est le signal.** « Vigie-Chiro » avec tiret désigne la plateforme — c'est déjà la forme
/// employée par l'IHM (« Téléverser sur Vigie-Chiro », « Se connecter à Vigie-Chiro… »). « VigieChiro »
/// sans tiret ne doit apparaître qu'accompagné de « PR Companion ».
///
/// Ce test ne couvre que `docs/`, le site **utilisateur**. `dev-docs/` parle abondamment de la
/// plateforme, à juste titre, et les [ADR](../../../../../../dev-docs/decisions) sont immuables.
///
/// ## Ce qui est exempté, et pourquoi
///
/// Les **noms d'artefacts produits par le build** — `VigieChiro.exe`, `VigieChiro-2.20.0-…AppImage`,
/// les lignes `sha256sum`. La documentation est **juste** tant que le build produit ces noms : les
/// renommer dans les pages sans renommer les fichiers la rendrait fausse. Les renommer dans le build
/// est une autre décision, qui touche les chemins d'installation existants.
class NomDeLApplicationTest {

    private static final Path DOCS = Path.of("docs");

    /// « VigieChiro » **non** suivi de « PR Companion », et **non** précédé d'un tiret (ce qui en
    /// ferait la plateforme).
    private static final Pattern NOM_NU = Pattern.compile("(?<!-)\\bVigieChiro\\b(?!\\s+PR\\s+Companion)");

    /// Un nom de fichier produit par le build, reconnu **des deux côtés** : le séparateur peut suivre
    /// (`VigieChiro.exe`, `VigieChiro-2.20.0-…`, `VigieChiro/bin/…`) **ou précéder**
    /// (`VigieChiro/bin/VigieChiro`, où le dernier segment est l'exécutable et n'est suivi de rien).
    ///
    /// Ne regarder qu'un seul côté laissait passer ce dernier cas.
    ///
    /// Le **caractère exigé après le séparateur** n'est pas un détail : sans lui, le point de fin de
    /// phrase (« …l'utilisation de VigieChiro. ») était pris pour un point d'extension, et la faute
    /// la plus banale traversait le test sans le faire rougir. Trouvé en mutant `docs/faq.md` et en
    /// n'obtenant pas le rouge attendu.
    ///
    /// Les **points de suspension** y figurent parce que la doc élide les versions dans ses tableaux
    /// de téléchargement (`VigieChiro-…-x64.msi`). Sans eux le test criait sur une ligne juste.
    private static final Pattern ARTEFACT_SUIT = Pattern.compile("VigieChiro[-./\\\\][A-Za-z0-9…]");

    private static final Pattern ARTEFACT_PRECEDE = Pattern.compile("[/\\\\]$");

    @Test
    @DisplayName("Le site utilisateur nomme l'application « VigieChiro PR Companion », pas « VigieChiro »")
    void la_doc_utilisateur_nomme_l_application_en_entier() {
        List<String> fautifs = new ArrayList<>();

        for (Path page : pages()) {
            List<String> lignes = lire(page);
            for (int i = 0; i < lignes.size(); i++) {
                String ligne = lignes.get(i);
                Matcher m = NOM_NU.matcher(ligne);
                while (m.find()) {
                    // On regarde autour de CETTE occurrence, pas la ligne entière : une même ligne peut
                    // porter un nom d'artefact et une prose fautive.
                    boolean suivi =
                            ARTEFACT_SUIT.matcher(ligne.substring(m.start())).lookingAt();
                    boolean precede = ARTEFACT_PRECEDE
                            .matcher(ligne.substring(0, m.start()))
                            .find();
                    if (!suivi && !precede) {
                        fautifs.add(page + ":" + (i + 1) + " — " + ligne.strip());
                    }
                }
            }
        }

        assertThat(fautifs).as("""
                        La documentation utilisateur appelle l'application « VigieChiro », qui est le
                        nom de la PLATEFORME à laquelle elle se connecte.

                        • L'application : « VigieChiro PR Companion » à la première mention d'une page,
                          « l'application » ensuite.
                        • La plateforme : « Vigie-Chiro », avec le tiret — c'est la forme qu'emploie
                          l'IHM (« Téléverser sur Vigie-Chiro »).

                        Le tiret est le signal, et c'est ce qui rend cette règle vérifiable.

                        Si l'occurrence est un NOM DE FICHIER produit par le build (VigieChiro.exe,
                        VigieChiro-2.20.0-…AppImage, VigieChiro/bin/VigieChiro), elle est légitime et
                        ce test l'exempte : il reconnaît un séparateur collé au nom, avant ou après.
                        Une exemption qui manque se corrige ici, pas en renommant le fichier.
                        """).isEmpty();
    }

    /// Les pages du site utilisateur, **et sa configuration** : `site_name` et `site_description`
    /// sont ce que le lecteur voit en premier, dans l'onglet du navigateur et dans les résultats de
    /// recherche. C'est là que le nom était faux au départ, donc c'est là qu'il faut le tenir.
    private static List<Path> pages() {
        try (Stream<Path> chemins = Files.walk(DOCS)) {
            List<Path> trouvees = new ArrayList<>(
                    chemins.filter(p -> p.toString().endsWith(".md")).sorted().toList());
            trouvees.add(Path.of("mkdocs.yml"));
            return trouvees;
        } catch (IOException echec) {
            throw new UncheckedIOException("lecture de " + DOCS, echec);
        }
    }

    private static List<String> lire(Path page) {
        try {
            return Files.readAllLines(page);
        } catch (IOException echec) {
            throw new UncheckedIOException("lecture de " + page, echec);
        }
    }
}
