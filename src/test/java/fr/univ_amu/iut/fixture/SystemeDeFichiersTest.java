package fr.univ_amu.iut.fixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Le prédicat dit-il vrai (#5433, passe 6) ?
///
/// Il garde **huit sites** de l'arbre de test, et aucun ne l'assertait. C'est la forme la plus
/// coûteuse d'un dispositif vert : un prédicat inversé ne casse rien, il fait **sauter en silence**
/// les huit cas qu'il gardait, et le rapport annonce des tests « sautés » que personne ne relit.
///
/// La propriété éprouvée n'est pas une tautologie. On ne compare pas le prédicat à l'expression
/// qu'il enveloppe, ce qui ne prouverait rien : on le confronte à ce que le système fait
/// **réellement** quand on lui demande de poser des permissions POSIX.
class SystemeDeFichiersTest {

    @TempDir
    Path dossier;

    @Test
    @DisplayName("#5433 : le prédicat s'accorde avec ce que le système accepte VRAIMENT")
    void le_predicat_dit_ce_que_le_systeme_fait() throws IOException {
        Path fichier = Files.writeString(dossier.resolve("cible.txt"), "peu importe");

        if (SystemeDeFichiers.posixDisponible()) {
            Files.setPosixFilePermissions(fichier, PosixFilePermissions.fromString("rw-------"));
            assertThat(Files.getPosixFilePermissions(fichier))
                    .as("le prédicat annonce POSIX, donc poser des permissions doit aboutir")
                    .isEqualTo(PosixFilePermissions.fromString("rw-------"));
        } else {
            assertThatThrownBy(
                            () -> Files.setPosixFilePermissions(fichier, PosixFilePermissions.fromString("rw-------")))
                    .as("le prédicat annonce l'absence de POSIX, donc poser des permissions doit être refusé")
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
