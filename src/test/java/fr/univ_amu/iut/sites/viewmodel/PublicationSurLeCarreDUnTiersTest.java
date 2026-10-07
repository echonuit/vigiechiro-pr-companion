package fr.univ_amu.iut.sites.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.FournisseurToken;
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
import fr.univ_amu.iut.sites.model.PublicationPoint;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointCommuneDao;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.PointPublieDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import fr.univ_amu.iut.sites.model.dao.SiteTiersDao;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// La **mention du carré d'un tiers** à la publication d'un point (#6132), au niveau du modèle de vue.
///
/// Trois carrés, parce que la mention ne vaut que par ce qu'elle distingue :
///
/// | Carré | État | Ce que l'écran doit dire |
/// |---|---|---|
/// | `tiers` | relié, marqué « tiers » | la mention |
/// | `aSoi` | relié, sans marque | rien |
/// | `jamaisRelie` | ni lien ni marque | rien : on ne sait pas, et on ne prétend pas qu'il est à soi |
///
/// Et une règle qui borne la mention : **elle n'empêche rien**. Chaque cas compare donc l'empêchement
/// du carré à celui qu'il aurait sans la marque.
class PublicationSurLeCarreDUnTiersTest {

    private static final String ID_USER = "u-1";

    @TempDir
    Path dossier;

    private LienVigieChiroDao liens;
    private PointPublieDao publies;
    private SiteTiersDao marque;
    private Site tiers;
    private Site aSoi;
    private Site jamaisRelie;

    @BeforeEach
    void preparer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
        ServiceSites service = new ServiceSites(
                new SiteDao(source),
                new PointDao(source),
                new PassageDao(source),
                new HorlogeFigee(LocalDate.of(2026, 10, 7)),
                new PointCommuneDao(source),
                () -> {});
        liens = new LienVigieChiroDao(source);
        publies = new PointPublieDao(source);
        marque = new SiteTiersDao(source);
        tiers = service.creerSite("640380", "Étang", Protocole.STANDARD, null, ID_USER);
        aSoi = service.creerSite("640381", "Lavoir", Protocole.STANDARD, null, ID_USER);
        jamaisRelie = service.creerSite("640382", "Bergerie", Protocole.STANDARD, null, ID_USER);
        relier(tiers, "6a4961f587bc8dba39481180");
        relier(aSoi, "6a4961f587bc8dba39481181");
        marque.marquer(tiers.id());
    }

    private void relier(Site site, String objectid) {
        liens.upsert(new LienVigieChiro(LienVigieChiro.ENTITE_SITE, String.valueOf(site.id()), objectid, false));
    }

    /// Publication installée et connectée : le cas où le geste est réellement offert.
    private PublicationDepuisLaFiche connectee() {
        FournisseurToken token = () -> Optional.of("jeton-de-test");
        return new PublicationDepuisLaFiche(
                publies, liens, marque, Optional.of(new PublicationPoint(new ClientVigieChiro(token), publies, token)));
    }

    private PublicationDepuisLaFiche nonInstallee() {
        return new PublicationDepuisLaFiche(publies, liens, marque, Optional.empty());
    }

    @Test
    @DisplayName("#6132 : la mention se dit sur le carré d'un tiers, avec le mot que l'écran emploie déjà")
    void la_mention_se_dit_sur_le_carre_d_un_tiers() {
        assertThat(connectee().mentionDuTiers(tiers.id()))
                .hasValue("Ce carré est celui d'un tiers : votre point s'ajoutera aux siens.");
    }

    @Test
    @DisplayName("#6132 : aucune mention sur un carré à soi, ni sur un carré jamais relié")
    void aucune_mention_sans_la_marque() {
        assertThat(connectee().mentionDuTiers(aSoi.id()))
                .as("relié et sans marque : rien à dire")
                .isEmpty();
        assertThat(connectee().mentionDuTiers(jamaisRelie.id()))
                .as("jamais relié : on ne sait pas à qui il est, donc on se tait")
                .isEmpty();
    }

    @Test
    @DisplayName("#6132 : la mention suit la marque dans les deux sens, comme la synchronisation la pose")
    void la_mention_suit_la_marque() {
        PublicationDepuisLaFiche publication = connectee();

        marque.definir(tiers.id(), false);
        assertThat(publication.mentionDuTiers(tiers.id()))
                .as("le carré a changé de main côté plateforme : la mention disparaît")
                .isEmpty();

        marque.definir(aSoi.id(), true);
        assertThat(publication.mentionDuTiers(aSoi.id())).isPresent();
    }

    @Test
    @DisplayName("#6132 : la mention n'empêche rien, le geste reste offert sur les trois carrés")
    void la_mention_n_empeche_rien() {
        PublicationDepuisLaFiche publication = connectee();

        assertThat(publication.empechement(tiers.id(), true))
                .as("carré d'un tiers, connecté, point placé : rien ne retient le geste")
                .isEmpty();
        assertThat(publication.empechement(aSoi.id(), true)).isEmpty();
        String motifSansLien = publication.empechement(jamaisRelie.id(), true).orElseThrow();
        assertThat(motifSansLien).contains("pas encore enregistré");

        // La marque posée partout, puis retirée partout : aucun des trois verdicts ne bouge. Un garde
        // glissé sur la marque ferait diverger l'une des deux lectures.
        for (Site site : new Site[] {tiers, aSoi, jamaisRelie}) {
            marque.definir(site.id(), true);
        }
        assertThat(publication.empechement(tiers.id(), true)).isEmpty();
        assertThat(publication.empechement(aSoi.id(), true)).isEmpty();
        assertThat(publication.empechement(jamaisRelie.id(), true)).hasValue(motifSansLien);
        assertThat(publication.empechement(tiers.id(), false))
                .as("le seul motif qui reste sur un carré de tiers est celui du point, pas celui du carré")
                .hasValueSatisfying(motif -> assertThat(motif).contains("coordonnées"));
    }

    @Test
    @DisplayName("#6132 : la case de la modale porte la mention, se coche, et le point part")
    void la_case_porte_la_mention_et_le_point_part() {
        IntentionPublication intention = new IntentionPublication(connectee(), () -> true);

        intention.aLaCreation(tiers.id());
        assertThat(intention.mentionDuTiersProperty().get())
                .isEqualTo(PublicationDepuisLaFiche.MENTION_CARRE_D_UN_TIERS);
        assertThat(intention.empechementProperty().get())
                .as("la mention n'est pas un motif de gris")
                .isEmpty();

        intention.demandeeProperty().set(true);
        intention.recalculer();
        assertThat(intention.demandeeProperty().get())
                .as("recalculer ne décoche pas la case d'un carré de tiers")
                .isTrue();
        assertThat(intention.pointAPublier(42L)).hasValue(42L);
    }

    @Test
    @DisplayName("#6132 : pas de mention dans la modale sur un carré à soi, jamais relié, ou en édition")
    void la_modale_se_tait_quand_il_n_y_a_rien_a_dire() {
        IntentionPublication intention = new IntentionPublication(connectee(), () -> true);

        intention.aLaCreation(aSoi.id());
        assertThat(intention.mentionDuTiersProperty().get()).isEmpty();
        intention.demandeeProperty().set(true);
        assertThat(intention.pointAPublier(42L)).hasValue(42L);

        intention.aLaCreation(jamaisRelie.id());
        assertThat(intention.mentionDuTiersProperty().get()).isEmpty();

        intention.aLaCreation(tiers.id());
        intention.aLEdition();
        assertThat(intention.mentionDuTiersProperty().get())
                .as("en édition il n'y a pas de case : la mention de l'ouverture d'avant ne reste pas")
                .isEmpty();
    }

    @Test
    @DisplayName("#6132 : sans publication installée, la modale n'offre ni case ni mention")
    void sans_publication_installee_pas_de_mention_dans_la_modale() {
        IntentionPublication intention = new IntentionPublication(nonInstallee(), () -> true);

        intention.aLaCreation(tiers.id());

        assertThat(intention.offerteProperty().get()).isFalse();
        assertThat(intention.mentionDuTiersProperty().get())
                .as("la mention accompagne un geste : sans case, elle ne décrit pas le carré")
                .isEmpty();
    }
}
