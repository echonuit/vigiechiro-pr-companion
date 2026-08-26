package fr.univ_amu.iut.commun.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.Workspace;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Le **verrou de workspace** (#2731, lot 1 du chantier de dette #2720).
///
/// Rien n'empêchait deux processus de travailler sur le même workspace : deux instances graphiques,
/// une IHM et une CLI, ou une restauration pendant un import. Toutes les garanties que ce lot vient
/// de poser (migration atomique, filet, restauration vérifiée) tombent si un second processus écrit
/// pendant l'opération.
///
/// Le verrou est un **verrou de fichier système** et non un fichier de PID : le système le relâche
/// quand le processus meurt, donc un plantage ne condamne pas le workspace. Le PID écrit dans le
/// fichier sert au **message**, jamais à la décision.
class VerrouWorkspaceTest {

    @TempDir
    Path racine;

    @Nested
    @DisplayName("Le repli de lecture, celui que seul Windows emprunte")
    class ApresLOctetDuVerrou {

        // Ces trois cas existent parce que PIT rendait QUATRE mutants sans couverture ici, dont la
        // borne `<= 0` et la soustraction (#3561, passe 6). La raison n'était pas un oubli : le repli
        // ne s'exécute que sous Windows, seul système où le verrou est impératif, donc aucune mesure
        // faite sous Linux ne pouvait l'atteindre. Le passage hebdomadaire sous Windows éprouve le
        // câblage ; ces cas éprouvent la BORNE, partout.

        @Test
        @DisplayName("un fichier vide ne rend rien plutôt qu'un octet fantôme")
        void fichier_vide() throws IOException {
            Path f = Files.writeString(racine.resolve("vide.lock"), "");

            assertThat(VerrouWorkspace.apresLOctetDuVerrou(f)).isEmpty();
        }

        @Test
        @DisplayName("un fichier réduit au seul octet du verrou ne rend rien non plus")
        void un_seul_octet() throws IOException {
            Path f = Files.writeString(racine.resolve("sentinelle-seule.lock"), "#");

            assertThat(VerrouWorkspace.apresLOctetDuVerrou(f))
                    .as("la borne est <= 0 : un octet moins l'octet du verrou, il ne reste rien à lire")
                    .isEmpty();
        }

        @Test
        @DisplayName("le contenu est rendu SANS l'octet du verrou, et en entier")
        void saute_le_premier_octet() throws IOException {
            Path f = Files.writeString(racine.resolve("occupe.lock"), "#alice@poste-42");

            assertThat(VerrouWorkspace.apresLOctetDuVerrou(f))
                    .as("un octet de trop ou de moins décale tout le nom lu")
                    .isEqualTo("alice@poste-42");
        }
    }

    @Test
    @DisplayName("le premier preneur obtient le verrou")
    void premier_preneur_obtient_le_verrou() {
        try (VerrouWorkspace verrou = VerrouWorkspace.prendre(workspace()).orElseThrow()) {
            assertThat(verrou.detenu()).isTrue();
        }
    }

    @Test
    @DisplayName("un second preneur ne l'obtient pas tant que le premier le tient")
    void second_preneur_refuse() {
        try (VerrouWorkspace premier = VerrouWorkspace.prendre(workspace()).orElseThrow()) {
            assertThat(premier.detenu()).isTrue();

            Optional<VerrouWorkspace> second = VerrouWorkspace.prendre(workspace());

            assertThat(second)
                    .as("deux processus sur le même workspace : le second doit repartir, pas écrire")
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("le verrou relâché est de nouveau prenable, et ne se dit plus détenu")
    void verrou_relache_est_reprenable() {
        VerrouWorkspace rendu = VerrouWorkspace.prendre(workspace()).orElseThrow();
        rendu.close();

        assertThat(rendu.detenu())
                .as("un verrou qui se dit encore détenu après avoir été rendu ferait croire une"
                        + " opération exclusive protégée alors qu'elle ne l'est plus")
                .isFalse();
        assertThat(VerrouWorkspace.prendre(workspace()))
                .as("fermer l'application doit rendre le workspace, sinon le verrou devient une prison")
                .isPresent();
    }

    @Test
    @DisplayName("le fichier de verrou dit qui l'occupe, pour que le refus soit lisible")
    void le_fichier_dit_qui_occupe() throws Exception {
        try (VerrouWorkspace verrou = VerrouWorkspace.prendre(workspace()).orElseThrow()) {
            assertThat(verrou.detenu()).isTrue();
            // Par `occupant` et non par `Files.readString` : cette dernière lit depuis l'octet 0,
            // donc traverse la zone verrouillée, et échoue sous Windows où un verrou est impératif
            // (#3693). Lire le fichier « en direct » ici éprouvait une capacité que le produit n'a pas.
            String contenu = VerrouWorkspace.occupant(workspace());

            assertThat(contenu)
                    .as("« le workspace est utilisé » sans dire par qui n'aide personne à s'en sortir")
                    .contains(String.valueOf(ProcessHandle.current().pid()));
        }
    }

    @Test
    @DisplayName("l'occupant est nommé au second preneur, qui n'a que le fichier pour le savoir")
    void occupant_lisible_par_le_second() {
        try (VerrouWorkspace premier = VerrouWorkspace.prendre(workspace()).orElseThrow()) {
            assertThat(premier.detenu()).isTrue();

            assertThat(VerrouWorkspace.occupant(workspace()))
                    .as("c'est ce que l'IHM et la CLI afficheront à la place d'un échec SQLite tardif")
                    .contains(String.valueOf(ProcessHandle.current().pid()));
        }
    }

    @Test
    @DisplayName("le processus qui détient déjà le verrou ne se bloque pas lui-même")
    void detenteur_ne_se_bloque_pas() {
        try (VerrouWorkspace verrou = VerrouWorkspace.prendre(workspace()).orElseThrow()) {
            assertThat(verrou.detenu()).isTrue();

            // L'IHM tient le verrou pour toute sa durée. Si une restauration lancée depuis cette même
            // IHM se heurtait au verrou de l'IHM, plus aucune opération exclusive ne serait possible.
            assertThatCode(() -> VerrouWorkspace.pourOperationExclusive(workspace(), "la restauration")
                            .close())
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("une opération exclusive sur un workspace libre le verrouille, puis le rend")
    void operation_exclusive_prend_et_rend() {
        try (VerrouWorkspace pendant = VerrouWorkspace.pourOperationExclusive(workspace(), "la migration")) {
            assertThat(VerrouWorkspace.prendre(workspace()))
                    .as("pendant l'opération, personne d'autre n'entre")
                    .isEmpty();
        }

        assertThat(VerrouWorkspace.prendre(workspace()))
                .as("et l'opération finie, le workspace est rendu : un verrou d'opération ne survit pas"
                        + " à son opération")
                .isPresent();
    }

    @Test
    @DisplayName("un verrou tenu par un tiers : le refus ne montre pas de parenthèses vides (#3571)")
    void occupant_inconnu_ne_laisse_pas_de_parentheses_vides() throws Exception {
        Files.createDirectories(racine.resolve("ws"));
        // Un verrou POSIX pris SANS rien inscrire : c'est ce que laisse un processus tiers, un fichier
        // tronqué, ou une tentative morte. Les tests de #3498 posaient exactement ce verrou-là, et
        // assertaient « déjà utilisé » sans jamais regarder ce qui suivait.
        try (FileChannel canal = FileChannel.open(
                        racine.resolve("ws").resolve(VerrouWorkspace.NOM_FICHIER),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE);
                FileLock ignore = canal.lock()) {

            assertThatThrownBy(() -> VerrouWorkspace.pourOperationExclusive(workspace(), "la migration"))
                    .isInstanceOf(RefusAvantEcriture.class)
                    .as("« déjà utilisé () » promet un nom et n'en donne aucun : c'est pire que de ne"
                            + " rien promettre")
                    .hasMessageNotContaining("()")
                    .hasMessageContaining("une autre instance");
        }
    }

    @Test
    @DisplayName("un verrou pris par l'application : le refus NOMME l'occupant (#3571)")
    void occupant_connu_est_nomme() {
        try (VerrouWorkspace tenu = VerrouWorkspace.prendre(workspace()).orElseThrow()) {
            // Depuis un autre « processus » du point de vue du verrou : ici on lit le fichier, ce que
            // fait le message. Sans ce test, le repli de l'occupant inconnu suffirait à tout couvrir,
            // et le nom pourrait disparaître sans que rien ne rougisse.
            assertThat(VerrouWorkspace.occupant(workspace()))
                    .as("le fichier porte de quoi retrouver le coupable")
                    .contains("processus " + ProcessHandle.current().pid());
        }
    }

    @Test
    @DisplayName("le complément d'occupant : le nom quand on l'a, rien du tout sinon (#3571)")
    void complement_d_occupant() {
        assertThat(VerrouWorkspace.complementOccupant("processus 4821, depuis 2026-08-03T21:14:07"))
                .as("l'instant se lit en français : la table de choix d'une sauvegarde écrit"
                        + " « 01/08/2026 12:15 » deux écrans plus loin (#3640)")
                .isEqualTo(" (processus 4821, depuis 03/08/2026 21:14)");
        assertThat(VerrouWorkspace.complementOccupant(""))
                .as("des parenthèses vides promettent un nom et n'en donnent aucun")
                .isEmpty();
        assertThat(VerrouWorkspace.complementOccupant("   \n  "))
                .as("un fichier tronqué ne laisse parfois que du blanc")
                .isEmpty();
        assertThat(VerrouWorkspace.complementOccupant(null))
                .as("et la lecture peut ne rien rendre du tout")
                .isEmpty();
    }

    @Test
    @DisplayName("#3640 : ce qui n'est pas un horodatage reconnu s'affiche tel quel")
    void complement_d_occupant_sur_ce_qui_n_est_pas_de_nous() {
        // Ce fichier est écrit par un processus et lu par un AUTRE, parfois d'une version différente,
        // parfois pas écrit par nous du tout. Ne reformater que ce qu'on reconnaît rend le repli
        // gratuit : pas de code de compatibilité, pas d'exception, et le comportement d'avant subsiste
        // exactement là où il doit subsister.
        assertThat(VerrouWorkspace.complementOccupant("processus 4821, depuis le 3 août à 21h"))
                .as("un verrou d'une version antérieure : verbatim, ni exception ni bouillie")
                .isEqualTo(" (processus 4821, depuis le 3 août à 21h)");
        assertThat(VerrouWorkspace.complementOccupant("verrou pose par un outil tiers"))
                .as("rien de reconnaissable : on n'invente pas")
                .isEqualTo(" (verrou pose par un outil tiers)");
        assertThat(VerrouWorkspace.complementOccupant("processus 4821, depuis 2026-13-45T99:99:99"))
                .as("une date impossible n'est pas une date : elle passe verbatim plutôt que de lever")
                .isEqualTo(" (processus 4821, depuis 2026-13-45T99:99:99)");
    }

    private Workspace workspace() {
        return new Workspace(racine.resolve("ws"));
    }

    @Test
    @DisplayName("#3693 : un verrou d'une version antérieure, sans sentinelle, se lit tel quel")
    void occupant_au_format_d_avant() throws IOException {
        // Écrit à la main, sans l'octet-sentinelle : c'est ce qu'une version antérieure laisse, et
        // c'est aussi ce qu'un outil tiers pourrait déposer. L'amputer du premier caractère
        // afficherait « rocessus 4821 », ce qui est pire que de ne rien dire.
        Path verrou = workspace().racine().resolve(VerrouWorkspace.NOM_FICHIER);
        Files.createDirectories(verrou.getParent());
        Files.writeString(verrou, "processus 4821, depuis 2026-08-03T21:14:07");

        assertThat(VerrouWorkspace.occupant(workspace()))
                .as("ne transformer que ce qu'on reconnaît rend la compatibilité gratuite")
                .isEqualTo("processus 4821, depuis 2026-08-03T21:14:07");
    }

    @Test
    @DisplayName("#3693 : un verrou posé sur TOUT le fichier exclut encore celui qui n'en veut qu'un octet")
    void cohabitation_avec_une_version_qui_verrouille_tout() throws Exception {
        // Le cas de la mise à jour : deux versions coexistent sur un poste. L'ancienne verrouille le
        // fichier entier, la nouvelle ne demande que l'octet 0. Les zones se chevauchent, donc
        // l'exclusion tient - et c'est la seule chose que ce verrou existe pour garantir.
        Path fichier = workspace().racine().resolve(VerrouWorkspace.NOM_FICHIER);
        Files.createDirectories(fichier.getParent());
        try (FileChannel ancienne = FileChannel.open(
                        fichier, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.READ);
                FileLock toutLeFichier = ancienne.lock()) {
            assertThat(toutLeFichier).isNotNull();

            assertThat(VerrouWorkspace.prendre(workspace()))
                    .as("une base corrompue coûte plus cher qu'un refus de trop pendant une mise à jour")
                    .isEmpty();
        }
    }
}
