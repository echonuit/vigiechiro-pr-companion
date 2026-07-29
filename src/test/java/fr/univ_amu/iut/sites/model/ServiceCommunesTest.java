package fr.univ_amu.iut.sites.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import fr.univ_amu.iut.commun.model.Commune;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.ResolveurCommune;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.sites.model.dao.PointCommuneDao;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Tenue à jour de la commune des points (#2791) : résolution ciblée après création ou changement
/// de GPS, rattrapage des seuls points en attente, et honnêteté du cas non résolu (une commune
/// absente vaut mieux qu'une commune fausse). Le résolveur est une lambda : aucun réseau.
class ServiceCommunesTest {

    private static final String ID_USER = "u-1";
    private static final Commune AIX = new Commune("Aix-en-Provence", "13001");

    @TempDir
    Path dossier;

    private PointDao points;
    private PointCommuneDao communes;
    private long idSite;

    @BeforeEach
    void preparer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
        idSite = new SiteDao(source)
                .insert(new Site(null, "130711", "Site test", Protocole.STANDARD, null, "2026-01-01", ID_USER))
                .id();
        points = new PointDao(source);
        communes = new PointCommuneDao(source);
    }

    private long insererPoint(String code, Double latitude, Double longitude) {
        return points.insert(new PointDEcoute(null, code, latitude, longitude, null, idSite))
                .id();
    }

    private ServiceCommunes service(ResolveurCommune resolveur) {
        return new ServiceCommunes(points, communes, resolveur);
    }

    @Test
    @DisplayName("mettreAJour résout et mémorise la commune d'un point géolocalisé")
    void mettre_a_jour_resout() {
        long idPoint = insererPoint("A1", 43.5297, 5.4474);

        Optional<Commune> resolue = service(position -> Optional.of(AIX)).mettreAJour(idPoint);

        assertThat(resolue).contains(AIX);
        assertThat(communes.pour(idPoint)).contains(AIX);
    }

    @Test
    @DisplayName("mettreAJour efface d'abord : hors ligne, la commune périmée ne survit pas")
    void mettre_a_jour_efface_avant() {
        long idPoint = insererPoint("A1", 43.5297, 5.4474);
        communes.definir(idPoint, new Commune("Venelles", "13113"));

        Optional<Commune> resolue = service(position -> Optional.empty()).mettreAJour(idPoint);

        assertThat(resolue).isEmpty();
        assertThat(communes.pour(idPoint))
                .as("une commune absente vaut mieux qu'une commune fausse")
                .isEmpty();
    }

    @Test
    @DisplayName("mettreAJour sur un point sans GPS : efface, ne résout rien, n'appelle pas le résolveur")
    void mettre_a_jour_sans_gps() {
        long idPoint = insererPoint("A1", null, null);
        communes.definir(idPoint, AIX);
        AtomicInteger appels = new AtomicInteger();

        Optional<Commune> resolue = service(position -> {
                    appels.incrementAndGet();
                    return Optional.of(AIX);
                })
                .mettreAJour(idPoint);

        assertThat(resolue).isEmpty();
        assertThat(communes.pour(idPoint)).isEmpty();
        assertThat(appels).hasValue(0);
    }

    @Test
    @DisplayName("mettreAJour refuse un point introuvable")
    void point_introuvable() {
        assertThatExceptionOfType(RegleMetierException.class)
                .isThrownBy(() -> service(position -> Optional.of(AIX)).mettreAJour(999L));
    }

    @Test
    @DisplayName("rattraper ne comble que les points en attente : GPS présent, commune absente")
    void rattraper_les_seuls_en_attente() {
        long enAttente = insererPoint("A1", 43.5297, 5.4474);
        long sansGps = insererPoint("B2", null, null);
        long dejaResolu = insererPoint("C3", 43.6, 5.5);
        communes.definir(dejaResolu, new Commune("Venelles", "13113"));
        AtomicInteger appels = new AtomicInteger();

        ServiceCommunes.BilanCommunes bilan = service(position -> {
                    appels.incrementAndGet();
                    return Optional.of(AIX);
                })
                .rattraper();

        assertThat(bilan.enAttente()).isEqualTo(1);
        assertThat(bilan.resolues()).isEqualTo(1);
        assertThat(bilan.restantes()).isZero();
        assertThat(appels)
                .as("un seul appel : ni le point sans GPS, ni le déjà résolu")
                .hasValue(1);
        assertThat(communes.pour(enAttente)).contains(AIX);
        assertThat(communes.pour(sansGps)).isEmpty();
        assertThat(communes.pour(dejaResolu))
                .as("le rattrapage ne retouche jamais une commune résolue")
                .contains(new Commune("Venelles", "13113"));
    }

    @Test
    @DisplayName("rattraper hors ligne : bilan honnête, les points restent en attente")
    void rattraper_hors_ligne() {
        insererPoint("A1", 43.5297, 5.4474);
        insererPoint("B2", 43.6, 5.5);

        ServiceCommunes.BilanCommunes bilan =
                service(position -> Optional.empty()).rattraper();

        assertThat(bilan.enAttente()).isEqualTo(2);
        assertThat(bilan.resolues()).isZero();
        assertThat(bilan.restantes()).isEqualTo(2);
    }

    @Test
    @DisplayName("pour relit la commune mémorisée")
    void lecture() {
        long idPoint = insererPoint("A1", 43.5297, 5.4474);
        communes.definir(idPoint, AIX);

        assertThat(service(position -> Optional.empty()).pour(idPoint)).contains(AIX);
    }
}
