package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import fr.univ_amu.iut.commun.model.EcritureAtomique.Deplacement;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Écriture d'un secret sur disque (#2735) : jamais dans un fichier plus permissif que lui, jamais à
/// moitié.
///
/// ⚠️ Ces tests éprouvent l'état **final**, et la fenêtre que l'issue corrige est un état
/// **intermédiaire** : après coup, écrire-puis-restreindre laisse exactement le même fichier. C'est
/// [fr.univ_amu.iut.architecture.SecretsEcritsProtegesTest] qui garde la forme d'écriture, faute de
/// pouvoir garder l'instant.
///
/// Survivants PIT **assumés**, tous de la même famille : le test « le système de fichiers est-il
/// POSIX » et le drapeau `secret`. Mesuré sous Java 25 avec un umask à `0002`, `Files.createTempFile`
/// sans attribut crée déjà `rw-------` (là où `Files.createFile` donne `rw-rw-r--`) : `ecrire` et
/// `ecrireSecret` produisent donc **le même fichier**, et aucun mutant qui les échange ne peut être tué.
///
/// Ce n'est pas une couverture manquante mais une **équivalence par construction**. Les deux méthodes
/// subsistent parce qu'elles ne promettent pas la même chose : `ecrireSecret` **exige** les permissions
/// et les garderait si le JDK changeait son défaut, `ecrire` s'en remet à lui. Et le choix au point
/// d'appel dit si le fichier est un secret, ce qu'aucune permission ne dira à sa place.
class EcritureAtomiqueTest {

    private static final Set<PosixFilePermission> PROPRIETAIRE_SEUL =
            Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

    @TempDir
    Path dossier;

    @Test
    @DisplayName("Création : le contenu est là, et le fichier n'est lisible que par son propriétaire")
    void creation_restreinte() throws IOException {
        Path cible = dossier.resolve("connexion.json");

        EcritureAtomique.ecrireSecret(cible, "{\"token\":\"secret\"}");

        assertThat(cible).hasContent("{\"token\":\"secret\"}");
        assertThat(permissions(cible)).containsExactlyInAnyOrderElementsOf(PROPRIETAIRE_SEUL);
        assertThat(temporairesResiduels()).isEmpty();
    }

    @Test
    @DisplayName("Réécriture par-dessus un fichier laissé permissif : le nouveau reste privé")
    void reecriture_ne_herite_pas_des_permissions_laxistes() throws IOException {
        assumeTrue(posixDisponible(), "système de fichiers non POSIX : permissions non applicables");
        Path cible = dossier.resolve("connexion.json");
        // Un fichier que quelqu'un aurait rendu lisible par tous : l'écriture ne doit pas s'y couler.
        Files.createFile(cible, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-rw-rw-")));
        Files.writeString(cible, "ancien");

        EcritureAtomique.ecrireSecret(cible, "nouveau");

        assertThat(cible).hasContent("nouveau");
        assertThat(permissions(cible)).containsExactlyInAnyOrderElementsOf(PROPRIETAIRE_SEUL);
        assertThat(temporairesResiduels()).isEmpty();
    }

    @Test
    @DisplayName("Écriture ordinaire (manifeste, export) : contenu remplacé, aucun temporaire résiduel")
    void ecriture_ordinaire_remplace_et_ne_laisse_rien() throws IOException {
        Path cible = dossier.resolve("connexion.json");
        Files.writeString(cible, "{\"racines\":[]}");

        EcritureAtomique.ecrire(cible, "{\"racines\":[\"a\"]}");

        assertThat(cible).hasContent("{\"racines\":[\"a\"]}");
        assertThat(temporairesResiduels()).isEmpty();
    }

    @Test
    @DisplayName("Échec de l'écriture : le contenu précédent survit, et aucun temporaire ne traîne")
    void echec_laisse_l_etat_precedent_intact() throws IOException {
        Path cible = dossier.resolve("connexion.json");
        Files.writeString(cible, "ancien");
        // Un dossier occupe la place du remplacement : le déplacement final ne peut pas aboutir.
        Path impossible = dossier.resolve("occupe");
        Files.createDirectory(impossible);
        Files.writeString(impossible.resolve("dedans"), "x");

        assertThatThrownBy(() -> EcritureAtomique.ecrire(impossible, "nouveau")).isInstanceOf(IOException.class);

        assertThat(cible).hasContent("ancien");
        assertThat(temporairesResiduels())
                .as("un temporaire abandonné garderait le secret sur le disque")
                .isEmpty();
    }

    private static boolean posixDisponible() {
        return FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
    }

    private static Set<PosixFilePermission> permissions(Path chemin) throws IOException {
        assumeTrue(posixDisponible(), "système de fichiers non POSIX : permissions non applicables");
        return Files.getPosixFilePermissions(chemin);
    }

    /// Tout ce qui traîne dans le dossier en dehors des fichiers que les tests y placent eux-mêmes.
    private List<Path> temporairesResiduels() throws IOException {
        try (Stream<Path> entrees = Files.list(dossier)) {
            return entrees.filter(Files::isRegularFile)
                    .filter(p -> !p.getFileName().toString().equals("connexion.json"))
                    .toList();
        }
    }

    @Nested
    @DisplayName("La cible tenue par un autre programme (#3777)")
    class CibleTenue {

        // ⚠️ Le déplacement est INJECTÉ, et il le faut : ce cas ne se produit que sous Windows, où un
        // lecteur concurrent bloque le remplacement. Sonde dispatchée sous Windows Server 2025 - un
        // simple `Files.newInputStream` suffit à provoquer l'`AccessDeniedException`, là où POSIX
        // renomme par-dessus un fichier ouvert quoi qu'il arrive. Sans cette couture, la reprise ne
        // serait éprouvable nulle part.
        //
        // L'attente est injectée aussi : un test qui dort vraiment 600 ms pour prouver une reprise
        // mesure la patience de la CI, pas le produit.

        @Test
        @DisplayName("Une tenue transitoire est traversée : l'écriture aboutit")
        void une_tenue_transitoire_est_traversee() throws IOException {
            Path cible = dossier.resolve("connexion.json");
            Files.writeString(cible, "ancien");
            Deplacement tenuDeuxFois = tenuPuisLibere(2);

            EcritureAtomique.ecrire(cible, "nouveau", false, tenuDeuxFois, millis -> {});

            assertThat(cible).hasContent("nouveau");
            assertThat(temporairesResiduels())
                    .as("une reprise qui laisse un temporaire derrière elle a échangé un défaut contre un autre")
                    .isEmpty();
        }

        @Test
        @DisplayName("Une tenue qui dure est refusée, et le refus NOMME la cause")
        void une_tenue_durable_est_refusee_en_le_disant() throws IOException {
            Path cible = dossier.resolve("connexion.json");
            Files.writeString(cible, "ancien");

            assertThatThrownBy(() -> EcritureAtomique.ecrire(cible, "nouveau", false, TOUJOURS_TENU, millis -> {}))
                    .isInstanceOf(IOException.class)
                    // ⚠️ On n'attend pas « Access denied » : l'utilisateur est PROPRIÉTAIRE du fichier,
                    // et ce message l'enverrait chercher un problème de droits qui n'existe pas.
                    .hasMessageContaining("tenu ouvert par un autre programme")
                    .hasMessageContaining("connexion.json")
                    .hasRootCauseInstanceOf(AccessDeniedException.class);

            assertThat(cible)
                    .as("le contenu précédent survit : c'est la promesse que la reprise ne doit pas casser")
                    .hasContent("ancien");
            assertThat(temporairesResiduels()).isEmpty();
        }

        @Test
        @DisplayName("Un refus qui n'est PAS une tenue ne se fait pas réessayer")
        void un_autre_echec_remonte_tout_de_suite() throws IOException {
            Path cible = dossier.resolve("connexion.json");
            Files.writeString(cible, "ancien");
            AtomicInteger essais = new AtomicInteger();
            Deplacement disqueMort = (source, destination) -> {
                essais.incrementAndGet();
                throw new IOException("No space left on device");
            };

            assertThatThrownBy(() -> EcritureAtomique.ecrire(cible, "nouveau", false, disqueMort, millis -> {}))
                    .isInstanceOf(IOException.class)
                    .hasMessage("No space left on device");

            assertThat(essais)
                    .as("insister sur un disque plein retarde le diagnostic sans rien changer")
                    .hasValue(1);
        }

        @Test
        @DisplayName("L'insistance est bornée : cinq tentatives, pas davantage")
        void l_insistance_est_bornee() throws IOException {
            Path cible = dossier.resolve("connexion.json");
            Files.writeString(cible, "ancien");
            AtomicInteger essais = new AtomicInteger();
            AtomicInteger attentes = new AtomicInteger();
            Deplacement compte = (source, destination) -> {
                essais.incrementAndGet();
                throw new AccessDeniedException(destination.toString());
            };

            assertThatThrownBy(() ->
                            EcritureAtomique.ecrire(cible, "n", false, compte, millis -> attentes.incrementAndGet()))
                    .isInstanceOf(IOException.class);

            assertThat(essais).as("cinq tentatives").hasValue(5);
            assertThat(attentes)
                    .as("quatre attentes seulement : on n'attend pas après la dernière tentative")
                    .hasValue(4);
        }

        @Test
        @DisplayName("Interrompu pendant l'attente : le drapeau d'interruption est RENDU")
        void l_interruption_est_rendue_au_thread() throws IOException {
            Path cible = dossier.resolve("connexion.json");
            Files.writeString(cible, "ancien");
            Deplacement toujoursTenu = (source, destination) -> {
                throw new AccessDeniedException(destination.toString());
            };

            assertThatThrownBy(() -> EcritureAtomique.ecrire(cible, "n", false, toujoursTenu, millis -> {
                        throw new InterruptedException("on ferme l'application");
                    }))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("interrompue");

            // ⚠️ Avaler une InterruptedException sans reposer le drapeau prive l'appelant de la seule
            // information qui lui dise d'arrêter : la fermeture demandée passerait inaperçue, et le
            // travail continuerait. `Thread.interrupted()` lit ET remet à zéro, ce qui laisse le test
            // suivant sur un thread propre.
            assertThat(Thread.interrupted())
                    .as("le drapeau d'interruption doit être rendu au thread appelant")
                    .isTrue();

            assertThat(cible).hasContent("ancien");
            assertThat(temporairesResiduels()).isEmpty();
        }

        /// ⚠️ Le seul cas qui traverse le **vrai** déplacement, avec un **vrai** lecteur.
        ///
        /// Les quatre au-dessus éprouvent la LOGIQUE de reprise sur un déplacement fabriqué ; celui-ci
        /// éprouve le CÂBLAGE - que le refus du système soit bien une `AccessDeniedException`, et
        /// qu'elle atteigne la boucle. Les deux sont nécessaires : l'un sans l'autre laisse la moitié
        /// du remède non jugée, comme le repli de lecture du verrou l'a montré (#3714).
        ///
        /// Il n'affirme donc pas la même chose selon la plateforme, et c'est assumé : sous POSIX le
        /// déplacement réussit malgré le lecteur, sous Windows il refuse. Ce qui vaut **partout** est
        /// l'invariant : le fichier porte l'ancien contenu ou le nouveau, **jamais un fragment**, et
        /// aucun temporaire ne reste. C'est la promesse de la classe, et elle ne dépend d'aucun système.
        @Test
        @DisplayName("Avec un VRAI lecteur : ça passe ou ça refuse, jamais un fichier à moitié")
        void un_vrai_lecteur_ne_casse_pas_l_invariant() throws IOException {
            Path cible = dossier.resolve("connexion.json");
            Files.writeString(cible, "ancien");

            try (var lecteur = Files.newInputStream(cible)) {
                try {
                    EcritureAtomique.ecrire(cible, "nouveau");
                } catch (IOException tenu) {
                    assertThat(tenu)
                            .as("un refus doit nommer la tenue, pas parler de droits")
                            .hasMessageContaining("tenu ouvert par un autre programme");
                }
            }

            assertThat(Files.readString(cible))
                    .as("l'un ou l'autre, jamais un fragment : c'est la promesse de la classe")
                    .isIn("ancien", "nouveau");
            assertThat(temporairesResiduels())
                    .as("un temporaire abandonné garderait le secret sur le disque")
                    .isEmpty();
        }

        /// Un déplacement qui refuse `refus` fois, puis laisse passer.
        private Deplacement tenuPuisLibere(int refus) {
            AtomicInteger restants = new AtomicInteger(refus);
            return (source, destination) -> {
                if (restants.getAndDecrement() > 0) {
                    throw new AccessDeniedException(destination.toString());
                }
                Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
            };
        }

        private final Deplacement TOUJOURS_TENU = (source, destination) -> {
            throw new AccessDeniedException(destination.toString());
        };
    }
}
