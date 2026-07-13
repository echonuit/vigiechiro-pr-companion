package fr.univ_amu.iut.sites.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.PointVigieChiro;
import fr.univ_amu.iut.commun.api.RapportSynchro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.api.SiteVigieChiro;
import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// Import + rapprochement des sites (#718), API **mockée**, DAO et [ServiceSites] **réels** (base
/// jetable). On vérifie la création d'un site absent (carré + points + lien verrouillé), le
/// rattachement d'un site déjà présent (sans doublon, idempotent) et la garde hors-ligne.
@ExtendWith(MockitoExtension.class)
class RapprochementSitesTest {

    private static final String ID_USER = "u-1";

    @TempDir
    Path dossier;

    @Mock
    private ClientVigieChiro client;

    private SiteDao siteDao;
    private PointDao pointDao;
    private ServiceSites service;
    private LienVigieChiroDao liens;
    private RapprochementSites rapprochement;

    @BeforeEach
    void preparer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
        siteDao = new SiteDao(source);
        pointDao = new PointDao(source);
        PassageDao passageDao = new PassageDao(source);
        service = new ServiceSites(siteDao, pointDao, passageDao, new HorlogeFigee(LocalDate.of(2026, 6, 1)));
        liens = new LienVigieChiroDao(source);
        rapprochement = new RapprochementSites(siteDao, service, liens, ID_USER);
    }

    private static SiteVigieChiro siteDistant(String id, String carre, List<PointVigieChiro> points) {
        return new SiteVigieChiro(id, "Vigiechiro - Point Fixe-" + carre, true, carre, points);
    }

    @Test
    @DisplayName("site absent : créé (carré + points), relié à son objectid, marqué verrouillé")
    void importe_un_site_absent() {
        when(client.mesSites())
                .thenReturn(ReponseApi.succes(List.of(siteDistant(
                        "s1",
                        "130711",
                        List.of(new PointVigieChiro("Z1", 43.52, 5.46), new PointVigieChiro("Z41", 43.51, 5.45))))));

        Optional<RapportSynchro> rapport = rapprochement.synchroniser(client);

        assertThat(rapport).contains(new RapportSynchro("sites", 1));
        List<Site> locaux = siteDao.findByUtilisateur(ID_USER);
        assertThat(locaux).singleElement().extracting(Site::numeroCarre).isEqualTo("130711");
        Site cree = locaux.getFirst();
        assertThat(pointDao.findBySite(cree.id()))
                .extracting(PointDEcoute::code)
                .containsExactly("Z1", "Z41");
        assertThat(liens.tous(LienVigieChiro.ENTITE_SITE)).containsOnly(Map.entry(String.valueOf(cree.id()), "s1"));
        assertThat(liens.verrouilles(LienVigieChiro.ENTITE_SITE)).containsExactly(String.valueOf(cree.id()));
    }

    @Test
    @DisplayName("site déjà présent (même carré) : relié sans re-création ; rejouer reste idempotent")
    void relie_un_site_existant_sans_doublon() {
        Site existant = service.creerSite("999999", "Mon carré", Protocole.STANDARD, null, ID_USER);
        when(client.mesSites()).thenReturn(ReponseApi.succes(List.of(siteDistant("s2", "999999", List.of()))));

        rapprochement.synchroniser(client);
        rapprochement.synchroniser(client); // rejeu : doit rester idempotent

        assertThat(siteDao.findByUtilisateur(ID_USER))
                .extracting(Site::numeroCarre)
                .containsExactly("999999");
        assertThat(liens.objectidPour(LienVigieChiro.ENTITE_SITE, String.valueOf(existant.id())))
                .contains("s2");
    }

    @Test
    @DisplayName("hors-ligne (aucun site distant) : rien créé, rapport vide")
    void hors_ligne_ne_cree_rien() {
        when(client.mesSites()).thenReturn(ReponseApi.succes(List.of()));

        assertThat(rapprochement.synchroniser(client)).isEmpty();
        assertThat(siteDao.findByUtilisateur(ID_USER)).isEmpty();
    }

    @Test
    @DisplayName("#1284 : injoignable → rien créé, rien purgé, mais la cause remonte")
    void injoignable_ne_touche_rien_mais_se_dit() {
        when(client.mesSites()).thenReturn(ReponseApi.injoignable("délai d'attente dépassé"));

        assertThat(rapprochement.synchroniser(client))
                .get()
                .satisfies(rapport -> assertThat(rapport.enClair())
                        .contains("sites non synchronisés")
                        .contains("injoignable"));
        assertThat(siteDao.findByUtilisateur(ID_USER)).isEmpty();

        when(client.mesSites()).thenReturn(ReponseApi.nonConnecte());
        assertThat(rapprochement.synchroniser(client))
                .as("non connecté : silence légitime")
                .isEmpty();
    }
}
