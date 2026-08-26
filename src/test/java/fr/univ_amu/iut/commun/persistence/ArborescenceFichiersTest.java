package fr.univ_amu.iut.commun.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.TailleFichier;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Les **deux** contrats d'effacement, et le fait qu'ils ne se confondent pas (#3574).
///
/// Sept variantes de ce geste vivaient dans le dépôt, avec quatre comportements en cas d'échec : lever,
/// lever sans le déclarer, avaler, ou rapporter. Elles portaient toutes des noms qui se ressemblaient,
/// si bien qu'une copie faite depuis le mauvais modèle changeait le comportement en cas de panne sans
/// rien casser de visible.
///
/// Ce qui rend ces tests possibles : un dossier **en lecture seule** refuse qu'on retire ce qu'il
/// contient. C'est la seule façon portable de fabriquer un fichier qui résiste, et elle vaut aussi pour
/// le cas réel - un support monté en lecture seule, un droit retiré.
class ArborescenceFichiersTest {

    @TempDir
    Path racine;

    @Test
    @DisplayName("effacerAuMieux ne lève pas, et rend ce qui a résisté")
    void efface_au_mieux_rend_ce_qui_resiste() throws IOException {
        Path verrouille = arborescenceQuiResiste();

        List<ArborescenceFichiers.EchecEffacement> restants =
                ArborescenceFichiers.effacerAuMieux(verrouille, suppressionQuiResiste(verrouille));

        assertThat(restants)
                .as("un nettoyage de temporaire ne doit pas transformer une opération réussie en échec,"
                        + " mais se taire laisserait l'appelant sans rien à dire")
                .isNotEmpty();
    }

    @Test
    @DisplayName("effacerAuMieux retire tout ce qui peut l'être, sans s'arrêter au premier récalcitrant")
    void efface_au_mieux_continue_apres_un_echec() throws IOException {
        // Un SEUL arbre, mixte : le récalcitrant est rencontré AVANT l'effaçable dans l'ordre inverse.
        // Deux dossiers séparés ne prouveraient rien du parcours - c'est l'erreur que la mutation a
        // révélée : arrêter au premier échec laissait le test vert.
        Path melange = Files.createDirectories(racine.resolve("melange"));
        Path libre = Files.writeString(melange.resolve("libre.txt"), "effaçable");
        Path verrou = Files.createDirectories(melange.resolve("verrou"));
        Files.writeString(verrou.resolve("tenu.txt"), "ne s'en va pas");

        List<ArborescenceFichiers.EchecEffacement> restants =
                ArborescenceFichiers.effacerAuMieux(melange, suppressionQuiResiste(verrou));

        assertThat(libre)
                .as("s'arrêter au premier échec laisserait derrière lui tout ce qui était effaçable")
                .doesNotExist();
        assertThat(restants).as("et ce qui a résisté est nommé, avec sa raison").isNotEmpty();
    }

    @Test
    @DisplayName("effacerAuMieux sur ce qui n'existe pas : rien à signaler")
    void efface_au_mieux_sur_l_absence() {
        List<ArborescenceFichiers.EchecEffacement> restants =
                ArborescenceFichiers.effacerAuMieux(racine.resolve("jamais-cree"));

        assertThat(restants)
                .as("supprimer ce qui est déjà absent est un succès, pas un cas limite")
                .isEmpty();
    }

    @Test
    @DisplayName("supprimerRecursivement lève quand quelque chose résiste : l'appelant décide")
    void supprimer_recursivement_leve() throws IOException {
        Path verrouille = arborescenceQuiResiste();

        assertThatThrownBy(() ->
                        ArborescenceFichiers.supprimerRecursivement(verrouille, suppressionQuiResiste(verrouille)))
                .as("une bascule de restauration qui ne parvient pas à retirer l'ancien dossier ne doit"
                        + " pas enchaîner sur le renommage comme si de rien n'était")
                .isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("supprimerRecursivement sur ce qui n'existe pas ne lève pas")
    void supprimer_recursivement_sur_l_absence() {
        assertThatCode(() -> ArborescenceFichiers.supprimerRecursivement(racine.resolve("jamais-cree")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("#3627 : peser rend le total ET nomme ce qu'elle n'a pas pu lire")
    void peser_nomme_ce_qu_elle_n_a_pas_pu_lire() throws IOException {
        Path dossier = Files.createDirectories(racine.resolve("nuit"));
        Files.writeString(dossier.resolve("lisible.wav"), "12345");
        Path opaque = Files.writeString(dossier.resolve("opaque.wav"), "ceci ne sera jamais pesé");

        ArborescenceFichiers.Pesee pesee = ArborescenceFichiers.peser(dossier, illisible(opaque));

        assertThat(pesee.octets())
                .as("le reste du dossier reste mesuré : s'arrêter au premier trou ne dirait pas combien"
                        + " pèse ce qu'on a pu lire, et l'appelant a besoin des deux")
                .isEqualTo(5L);
        assertThat(pesee.illisibles())
                .as("sans cette liste, le zéro du fichier opaque se confond avec un fichier vide")
                .extracting(ArborescenceFichiers.EchecLecture::chemin)
                .containsExactly(opaque);
        assertThat(pesee.complete()).isFalse();
    }

    @Test
    @DisplayName("#3627 : tout lisible, la pesée se déclare complète")
    void peser_sur_un_dossier_entierement_lisible() throws IOException {
        Path dossier = Files.createDirectories(racine.resolve("nuit-saine"));
        Files.writeString(dossier.resolve("a.wav"), "123");
        Files.writeString(dossier.resolve("b.wav"), "45");

        ArborescenceFichiers.Pesee pesee = ArborescenceFichiers.peser(dossier, TailleFichier.reelle());

        // Sans ce cas, `complete()` pourrait rendre `false` en toutes circonstances et l'autre test
        // resterait vert : le garde refuserait alors TOUTE sauvegarde, ce qui est une panne aussi.
        assertThat(pesee.octets()).isEqualTo(5L);
        assertThat(pesee.complete()).isTrue();
    }

    @Test
    @DisplayName("#3634 : un dossier illisible est rapporté, et le reste est quand même mesuré")
    void peser_rapporte_un_dossier_illisible_et_continue() throws IOException {
        Path dossier = Files.createDirectories(racine.resolve("nuit"));
        Files.writeString(dossier.resolve("lisible.wav"), "12345");
        Path interdit = Files.createDirectories(dossier.resolve("cache"));
        Files.writeString(interdit.resolve("dedans.wav"), "ce qu'on ne verra pas");

        {
            ArborescenceFichiers.Pesee pesee =
                    ArborescenceFichiers.peser(dossier, TailleFichier.reelle(), listageQuiRefuse(interdit));

            assertThat(pesee.octets())
                    .as("s'arrêter au premier dossier interdit ne dirait pas ce que pèse le reste, et"
                            + " l'appelant qui veut refuser a besoin des deux")
                    .isEqualTo(5L);
            assertThat(pesee.illisibles())
                    .as("un dossier qu'on n'a pas pu ouvrir est exactement ce qu'une mesure doit savoir dire")
                    .extracting(ArborescenceFichiers.EchecLecture::chemin)
                    .containsExactly(interdit);
        }
    }

    /// Une pesée qui échoue sur un fichier précis, et se comporte normalement sur les autres.
    ///
    /// C'est le port qui rend ce test possible : `Files.size` ne lève pas sur commande. Un dossier
    /// en `chmod 000` ferait échouer le **parcours** avant la pesée, et un lien mort est écarté par
    /// `isRegularFile` - la panne réelle n'a pas d'équivalent portable qu'on puisse fabriquer.
    private TailleFichier illisible(Path interdit) {
        return fichier -> {
            if (fichier.equals(interdit)) {
                throw new IOException("Permission non accordée");
            }
            return Files.size(fichier);
        };
    }

    @Test
    @DisplayName("#3632 : effacerAuMieux tient sa promesse de ne jamais lever, même sur un dossier fermé")
    void efface_au_mieux_ne_leve_pas_sur_un_dossier_ferme() throws IOException {
        Path temporaire = Files.createDirectories(racine.resolve("zip-extrait"));

        // Elle est appelée dans des `finally` (Importer, ImportationViewModel) : une exception levée
        // là REMPLACE le résultat de l'opération, donc un import réussi ressortirait en échec brut à
        // cause de son ménage. C'est ce que « ne lève jamais » existe pour empêcher.
        List<ArborescenceFichiers.EchecEffacement> restants = new ArrayList<>();
        assertThatCode(() ->
                        restants.addAll(ArborescenceFichiers.effacerAuMieux(temporaire, parcoursQuiEchoue(temporaire))))
                .doesNotThrowAnyException();
        assertThat(restants)
                .as("se taire ne suffit pas : l'appelant doit pouvoir dire CE QUI a résisté")
                .isNotEmpty();
    }

    @Test
    @DisplayName("#3632 : supprimerRecursivement lève ce qu'elle annonce, pas une exception non déclarée")
    void supprimer_recursivement_leve_en_ioexception_sur_un_dossier_ferme() throws IOException {
        Path cible = Files.createDirectories(racine.resolve("a-supprimer"));

        // `Files.walk` enveloppe son échec de parcours dans une `UncheckedIOException`, qui n'hérite
        // PAS d'`IOException` : sans rattrapage, elle traverse la signature déclarée et le diagnostic
        // de l'appelant ne s'applique jamais.
        assertThatThrownBy(() -> ArborescenceFichiers.supprimerRecursivement(cible, parcoursQuiEchoue(cible)))
                .isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("#3632 : copier lève ce qu'elle annonce sur une origine partiellement illisible")
    void copier_leve_en_ioexception_sur_un_dossier_ferme() throws IOException {
        Path origine = Files.createDirectories(racine.resolve("origine"));

        assertThatThrownBy(
                        () -> ArborescenceFichiers.copier(origine, racine.resolve("copie"), parcoursQuiEchoue(origine)))
                .isInstanceOf(IOException.class);
    }

    /// Un arbre ordinaire. Ce qui **résiste** ne vient plus du système mais du double
    /// [#suppressionQuiResiste] : `File.setWritable(false)` n'empêche pas la suppression sous Windows,
    /// et la fixture échouait donc **avant** que le test n'éprouve quoi que ce soit (#3525).
    private Path arborescenceQuiResiste() throws IOException {
        Path dossier = Files.createDirectories(racine.resolve("verrouille"));
        Files.writeString(dossier.resolve("tenu.txt"), "ne s'en va pas");
        return dossier;
    }

    /// Des gestes réels, sauf sur `interdit` que la suppression refuse.
    ///
    /// Fabriqué plutôt que demandé au système : `File.setWritable(false)` n'empêche pas la
    /// suppression sous Windows, et sept tests de cette classe le disaient en échouant au premier
    /// passage de la matrice trois plateformes (#3525). Un test qui ne prouve rien hors POSIX ne prouve
    /// rien sur la plateforme qui a le plus de façons de refuser un accès.
    private static GestesFichiers suppressionQuiResiste(Path interdit) {
        return new GestesFichiers() {
            @Override
            public void supprimer(Path chemin) throws IOException {
                if (chemin.startsWith(interdit)) {
                    throw new IOException("Suppression refusée : " + chemin);
                }
                Files.deleteIfExists(chemin);
            }
        };
    }

    /// Des gestes réels, sauf le parcours, qui échoue **pendant l'itération**.
    ///
    /// C'est le point du défaut #3632 : `Files.walk` n'annonce pas l'échec de parcours à la
    /// construction du flux mais à sa **consommation**, enveloppé dans une `UncheckedIOException` que
    /// le `catch (IOException)` voisin ne voit pas. Un double qui lèverait tout de suite éprouverait
    /// autre chose.
    private static GestesFichiers parcoursQuiEchoue(Path racine) {
        return new GestesFichiers() {
            @Override
            public Stream<Path> parcourir(Path aParcourir) {
                return Stream.of(aParcourir).peek(chemin -> {
                    throw new UncheckedIOException(
                            new AccessDeniedException(racine.resolve("ferme").toString()));
                });
            }
        };
    }

    /// Des gestes réels, sauf le listage de `interdit`, qui refuse de s'ouvrir.
    ///
    /// L'échec arrive à l'**ouverture** et non pendant l'itération, contrairement à
    /// [#parcoursQuiEchoue] : c'est ce qui distingue une pesée, qui note et continue, d'un parcours
    /// récursif, qui s'arrête.
    private static GestesFichiers listageQuiRefuse(Path interdit) {
        return new GestesFichiers() {
            @Override
            public Stream<Path> lister(Path dossier) throws IOException {
                if (dossier.equals(interdit)) {
                    throw new AccessDeniedException(dossier.toString());
                }
                return Files.list(dossier);
            }
        };
    }
}
