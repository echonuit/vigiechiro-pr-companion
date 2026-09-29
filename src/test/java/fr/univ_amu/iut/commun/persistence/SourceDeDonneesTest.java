package fr.univ_amu.iut.commun.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.Workspace;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.sqlite.SQLiteDataSource;

/// Une ouverture SQLite interrompue rend la connexion qu'elle a déjà obtenue.
class SourceDeDonneesTest {

    @TempDir
    Path racine;

    @Test
    void ferme_la_connexion_si_un_pragma_echoue() throws SQLException {
        Connection connexion = mock(Connection.class);
        Statement instruction = mock(Statement.class);
        when(connexion.createStatement()).thenReturn(instruction);
        doThrow(new SQLException("PRAGMA refusé")).when(instruction).execute(anyString());

        try (MockedConstruction<SQLiteDataSource> sources = mockConstruction(
                SQLiteDataSource.class,
                (source, contexte) -> when(source.getConnection()).thenReturn(connexion))) {
            SourceDeDonnees source = new SourceDeDonnees(new Workspace(racine));

            assertThatThrownBy(source::getConnection)
                    .isInstanceOf(DataAccessException.class)
                    .hasCauseInstanceOf(SQLException.class)
                    .hasRootCauseMessage("PRAGMA refusé");
            verify(connexion).close();
        }
    }
}
