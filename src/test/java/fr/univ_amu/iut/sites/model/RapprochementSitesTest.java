package fr.univ_amu.iut.sites.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.PointVigieChiro;
import fr.univ_amu.iut.commun.api.ProfilVigieChiro;
import fr.univ_amu.iut.commun.api.RapportSynchro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.api.SiteVigieChiro;
import fr.univ_amu.iut.commun.model.Commune;
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
import fr.univ_amu.iut.sites.model.dao.PointCommuneDao;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import fr.univ_amu.iut.sites.model.dao.SiteTiersDao;
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
    private PointCommuneDao communeDao;
    private ServiceSites service;
    private LienVigieChiroDao liens;
    private SiteTiersDao siteTiers;
    private RapprochementSites rapprochement;

    @BeforeEach
    void preparer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
        siteDao = new SiteDao(source);
        pointDao = new PointDao(source);
        PassageDao passageDao = new PassageDao(source);
        communeDao = new PointCommuneDao(source);
        service = new ServiceSites(
                siteDao, pointDao, passageDao, new HorlogeFigee(LocalDate.of(2026, 6, 1)), communeDao, () -> {});
        liens = new LienVigieChiroDao(source);
        siteTiers = new SiteTiersDao(source);
        rapprochement = new RapprochementSites(
                siteDao,
                service,
                liens,
                siteTiers,
                ID_USER,
                new ServiceCommunes(pointDao, communeDao, position -> Optional.empty()));
    }

    /// Site distant **sans propriétaire déclaré** (`observateur` absent) : cas des tests antérieurs à
    /// #2525, où la propriété du carré n'entrait pas en jeu.
    private static SiteVigieChiro siteDistant(String id, String carre, List<PointVigieChiro> points) {
        return siteDistant(id, carre, points, null);
    }

    private static SiteVigieChiro siteDistant(
            String id, String carre, List<PointVigieChiro> points, String observateur) {
        return new SiteVigieChiro(id, "Vigiechiro - Point Fixe-" + carre, true, carre, observateur, points);
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
                .as("les points rapatriés sont marqués synchronisés : la fiche site pourra les masquer (#1738)")
                .allMatch(PointDEcoute::synchronise)
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
                        .contains("sites non récupérés")
                        .contains("injoignable"));
        assertThat(siteDao.findByUtilisateur(ID_USER)).isEmpty();

        when(client.mesSites()).thenReturn(ReponseApi.nonConnecte());
        assertThat(rapprochement.synchroniser(client))
                .as("non connecté : silence légitime")
                .isEmpty();
    }

    // --- #2525 : propriété du carré dérivée de `site.observateur` ---

    /// Identifiant du carré local créé pour `carre` (les tests ci-dessous vérifient son marquage).
    private long idLocal(String carre) {
        return siteDao.findByUtilisateur(ID_USER).stream()
                .filter(site -> site.numeroCarre().equals(carre))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Carré absent en local : " + carre))
                .id();
    }

    @Test
    @DisplayName("#2525 : le carré d'un autre observateur est marqué « tiers », le sien ne l'est pas")
    void derive_la_propriete_du_carre() {
        when(client.moi()).thenReturn(ReponseApi.succes(new ProfilVigieChiro("moi-42", "Testeur", "Observateur")));
        when(client.mesSites())
                .thenReturn(ReponseApi.succes(List.of(
                        siteDistant("s1", "130711", List.of(), "moi-42"),
                        siteDistant("s2", "130712", List.of(), "quelqu-un-dautre"))));

        rapprochement.synchroniser(client);

        assertThat(siteTiers.estTiers(idLocal("130711"))).as("mon propre carré").isFalse();
        assertThat(siteTiers.estTiers(idLocal("130712"))).as("carré d'un tiers").isTrue();
    }

    @Test
    @DisplayName("#2525 : la propriété est réévaluée à chaque synchro (un carré peut changer de main)")
    void propriete_reevaluee_a_chaque_synchro() {
        when(client.moi()).thenReturn(ReponseApi.succes(new ProfilVigieChiro("moi-42", "Testeur", "Observateur")));
        when(client.mesSites())
                .thenReturn(ReponseApi.succes(List.of(siteDistant("s1", "130711", List.of(), "quelqu-un-dautre"))));
        rapprochement.synchroniser(client);
        assertThat(siteTiers.estTiers(idLocal("130711"))).isTrue();

        // Le carré m'est transféré côté plateforme : la synchro suivante retire le marquage.
        when(client.mesSites())
                .thenReturn(ReponseApi.succes(List.of(siteDistant("s1", "130711", List.of(), "moi-42"))));
        rapprochement.synchroniser(client);

        assertThat(siteTiers.estTiers(idLocal("130711"))).isFalse();
    }

    @Test
    @DisplayName("#2525 : sans profil lisible, aucun carré n'est présumé « tiers »")
    void sans_profil_aucun_carre_presume_tiers() {
        when(client.moi()).thenReturn(ReponseApi.injoignable("profil illisible"));
        when(client.mesSites())
                .thenReturn(ReponseApi.succes(List.of(siteDistant("s1", "130711", List.of(), "quelqu-un-dautre"))));

        rapprochement.synchroniser(client);

        assertThat(siteTiers.estTiers(idLocal("130711")))
                .as("sans preuve du contraire, on ne présume pas un tiers")
                .isFalse();
    }

    // --- Communes des points rapatriés (#2791) ---

    @Test
    @DisplayName("La synchro rattrape les communes des points qu'elle vient de créer (#2791)")
    void rattrape_les_communes_des_points_rapatries() {
        RapprochementSites avecResolveur = new RapprochementSites(
                siteDao,
                service,
                liens,
                siteTiers,
                ID_USER,
                new ServiceCommunes(
                        pointDao, communeDao, position -> Optional.of(new Commune("Aix-en-Provence", "13001"))));
        when(client.mesSites())
                .thenReturn(ReponseApi.succes(List.of(siteDistant(
                        "s1",
                        "640380",
                        List.of(new PointVigieChiro("Z1", 43.52, 5.46), new PointVigieChiro("Z41", 43.51, 5.45))))));

        avecResolveur.synchroniser(client);

        assertThat(communeDao.idsResolus())
                .as("les deux points rapatriés, géolocalisés, ont leur commune sitôt la synchro finie")
                .hasSize(2);
    }

    @Test
    @DisplayName("Un rattrapage qui échoue ne fait pas échouer la synchro (#2791)")
    void rattrapage_en_echec_reste_silencieux() {
        when(client.mesSites())
                .thenReturn(ReponseApi.succes(
                        List.of(siteDistant("s1", "640380", List.of(new PointVigieChiro("Z1", 43.52, 5.46))))));
        RapprochementSites rattrapageCasse = new RapprochementSites(
                siteDao, service, liens, siteTiers, ID_USER, new ServiceCommunes(pointDao, communeDao, position -> {
                    throw new IllegalStateException("panne du rattrapage");
                }));

        Optional<RapportSynchro> rapport = rattrapageCasse.synchroniser(client);

        assertThat(rapport)
                .as("la commune est un confort : la synchro rend son rapport")
                .isPresent();
    }
}
