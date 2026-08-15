package fr.univ_amu.iut.sites.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.PointVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.api.SiteVigieChiro;
import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// **Rapatrier un carré par son numéro** (#3806), sans l'avoir déposé ni le posséder.
///
/// ## Le cercle que ce service casse
///
/// Une nuit *opportuniste* se prépare en déclarant le carré puis le point, avant de déposer. Mais le
/// dépôt exige que le site local porte un **lien** vers son homologue plateforme
/// (`SynchronisationParticipation#creerPour`), et la synchronisation qui pose ce lien part de
/// `GET /moi/participations` : elle n'atteint donc que les carrés où une nuit est **déjà** déposée.
///
/// > Déposer était la seule chose qui aurait créé la participation qui aurait rendu le dépôt possible.
///
/// Mesuré le 2026-08-15 sur un compte réel : `/moi/sites` rend **0** là où `/moi/participations` rend un
/// site, dont le propriétaire est quelqu'un d'autre - les possesseurs de carrés sont peu nombreux, et la
/// majorité des observateurs déposent sur des carrés qui ne leur appartiennent pas.
///
/// `GET /sites?q=<carré>` rend ce site à qui le demande, avec ses localités positionnées : de quoi poser
/// le lien **avant** tout dépôt.
@ExtendWith(MockitoExtension.class)
class RapatriementCarreTest {

    private static final String ID_USER = "u-1";
    private static final String CARRE = "130711";

    @TempDir
    Path dossier;

    @Mock
    private ClientVigieChiro client;

    private SiteDao siteDao;
    private PointDao pointDao;
    private LienVigieChiroDao liens;
    private RapatriementCarre rapatriement;

    @BeforeEach
    void preparer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
        siteDao = new SiteDao(source);
        pointDao = new PointDao(source);
        PointCommuneDao communeDao = new PointCommuneDao(source);
        ServiceSites service = new ServiceSites(
                siteDao,
                pointDao,
                new PassageDao(source),
                new HorlogeFigee(LocalDate.of(2026, 8, 15)),
                communeDao,
                () -> {});
        liens = new LienVigieChiroDao(source);
        rapatriement = new RapatriementCarre(
                client,
                new ImportSiteDistant(
                        siteDao,
                        service,
                        liens,
                        new SiteTiersDao(source),
                        ID_USER,
                        new ServiceCommunes(pointDao, communeDao, position -> Optional.empty())));
    }

    private static SiteVigieChiro siteDistant(List<PointVigieChiro> points, String proprietaire) {
        return new SiteVigieChiro("6a49", "Vigiechiro - Point Fixe-" + CARRE, true, CARRE, proprietaire, points);
    }

    @Test
    @DisplayName("#3806 : un carré rapatrié est RATTACHÉ, donc le dépôt ne le refusera plus")
    void un_carre_rapatrie_est_rattache() {
        when(client.chercherCarre(CARRE))
                .thenReturn(ReponseApi.succes(
                        List.of(siteDistant(List.of(new PointVigieChiro("Z41", 43.51, 5.45)), "un-tiers"))));

        RapatriementCarre.Resultat resultat = rapatriement.rapatrier(CARRE);

        // C'est le fait qui porte tout le lot : sans ce lien, `SynchronisationParticipation#creerPour`
        // refuse la participation avec « Site non rattaché à Vigie-Chiro », et le téléversement s'arrête
        // là (#3463) - alors même que déposer est ce qui aurait créé le rattachement.
        assertThat(resultat).isInstanceOf(RapatriementCarre.Resultat.Rapatrie.class);
        Site local = siteDao.findByUtilisateur(ID_USER).getFirst();
        assertThat(liens.objectidPour(LienVigieChiro.ENTITE_SITE, String.valueOf(local.id())))
                .as("le lien vers l'identifiant plateforme est ce que le dépôt exige, et rien d'autre")
                .contains("6a49");
    }

    @Test
    @DisplayName("#3806 : les localités arrivent POSITIONNÉES : il n'y a plus rien à ressaisir")
    void les_localites_arrivent_positionnees() {
        when(client.chercherCarre(CARRE))
                .thenReturn(ReponseApi.succes(List.of(siteDistant(
                        List.of(
                                new PointVigieChiro("Z1", 43.522194, 5.465893),
                                new PointVigieChiro("Z41", 43.514558, 5.451322)),
                        "un-tiers"))));

        rapatriement.rapatrier(CARRE);

        // Le retour d'origine tenait en une phrase : « j'ai dû recréer Z1 manuellement en le positionnant
        // (en le choisissant dans la liste, il n'était pas prépositionné) ».
        Site local = siteDao.findByUtilisateur(ID_USER).getFirst();
        assertThat(pointDao.findBySite(local.id()))
                .extracting(PointDEcoute::code, PointDEcoute::latitude, PointDEcoute::longitude)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("Z1", 43.522194, 5.465893),
                        org.assertj.core.groups.Tuple.tuple("Z41", 43.514558, 5.451322));
    }

    @Test
    @DisplayName("#3806 : un carré introuvable ne crée rien")
    void un_carre_introuvable_ne_cree_rien() {
        when(client.chercherCarre("999999")).thenReturn(ReponseApi.succes(List.of()));

        assertThat(rapatriement.rapatrier("999999")).isInstanceOf(RapatriementCarre.Resultat.Inexistant.class);
        assertThat(siteDao.findByUtilisateur(ID_USER)).isEmpty();
    }

    @Test
    @DisplayName("#3806 : hors connexion, on ne crée rien et on ne prétend pas avoir cherché")
    void hors_connexion_rien_n_est_cree() {
        when(client.chercherCarre(CARRE)).thenReturn(ReponseApi.nonConnecte());

        assertThat(rapatriement.rapatrier(CARRE)).isInstanceOf(RapatriementCarre.Resultat.Indisponible.class);
        assertThat(siteDao.findByUtilisateur(ID_USER)).isEmpty();
    }
}
