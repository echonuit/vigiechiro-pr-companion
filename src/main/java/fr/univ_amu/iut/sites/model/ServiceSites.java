package fr.univ_amu.iut.sites.model;

import fr.univ_amu.iut.commun.model.Alerte;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.JournalMutations;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.ResultatVerification;
import fr.univ_amu.iut.commun.model.validation.ValidateurCarre;
import fr.univ_amu.iut.commun.model.validation.ValidateurCodePoint;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.sites.model.dao.PointCommuneDao;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.util.List;
import java.util.Objects;

/// Service métier de la feature `sites` : **service de référence** du projet (le patron à
/// recopier pour `passage`, `qualification`, etc.).
///
/// Principes (cf. CM4) :
///
/// - **Pure Java, testable JUnit, sans aucun import JavaFX** : la logique métier vit en
///   `<feature>/model`, l'IHM viendra par-dessus.
/// - **Reçoit ses dépendances par constructeur** (DAO + [Horloge]), fournies par Guice en
///   production (`SitesModule`) et instanciées à la main dans les tests.
/// - **Orchestre les DAO** : il ne contient pas de SQL (qui reste dans `model.dao`),
///   seulement des règles et des enchaînements d'appels.
/// - **Distingue règles soft et dures** : les rappels non bloquants (R3/R4) sont renvoyés
///   sous forme de [ResultatVerification] ; les refus (R5, intégrité) lèvent une
///   [RegleMetierException] ; les saisies mal formées (R1/R2) lèvent une
///   [IllegalArgumentException] via les validateurs.
///
/// Dépendance inter-feature assumée : le service connaît [PassageDao] (lecture seule) pour
/// protéger la suppression d'un site. La dépendance va `sites → passage` (et jamais
/// l'inverse), le graphe reste acyclique (contrôlé par `ArchitectureTest`).
public class ServiceSites {

    private final SiteDao siteDao;
    private final PointDao pointDao;
    private final PassageDao passageDao;
    private final Horloge horloge;
    private final PointCommuneDao communes;
    private final JournalMutations journal;

    public ServiceSites(
            SiteDao siteDao,
            PointDao pointDao,
            PassageDao passageDao,
            Horloge horloge,
            PointCommuneDao communes,
            JournalMutations journal) {
        this.siteDao = Objects.requireNonNull(siteDao, "siteDao");
        this.pointDao = Objects.requireNonNull(pointDao, "pointDao");
        this.passageDao = Objects.requireNonNull(passageDao, "passageDao");
        this.horloge = Objects.requireNonNull(horloge, "horloge");
        this.communes = Objects.requireNonNull(communes, "communes");
        this.journal = Objects.requireNonNull(journal, "journal");
    }

    /// Crée un site de suivi (P1).
    ///
    /// - R1 (dur) : le numéro de carré doit être valide (6 chiffres), cf. [ValidateurCarre].
    /// - Protocole : `PointFixeStandard` par défaut si `null` (P1).
    /// - R5 (dur) : le carré doit être unique pour cet utilisateur.
    /// - Date de création : lue de l'[Horloge] (déterministe en test).
    ///
    /// @return le site inséré, avec son `id` auto-généré
    /// @throws IllegalArgumentException si le numéro de carré est mal formé (R1)
    /// @throws RegleMetierException si le carré est déjà déclaré pour cet utilisateur (R5)
    public Site creerSite(
            String numeroCarre, String nomConvivial, Protocole protocole, String commentaire, String idUtilisateur) {
        ValidateurCarre.exigerValide(numeroCarre); // R1
        Objects.requireNonNull(idUtilisateur, "idUtilisateur");
        Protocole effectif = protocole != null ? protocole : Protocole.STANDARD;
        exigerCarreUniquePourUtilisateur(numeroCarre, idUtilisateur); // R5
        Site aCreer = new Site(
                null,
                numeroCarre,
                nomConvivial,
                effectif,
                commentaire,
                horloge.aujourdhui().toString(),
                idUtilisateur);
        Site cree = siteDao.insert(aCreer);
        journal.mutationStructurelleValidee();
        return cree;
    }

    /// Modifie la fiche d'un site existant (bouton « ✏ Modifier » de M-Site-detail).
    ///
    /// - Le site doit exister.
    /// - R1 (dur) : le numéro de carré doit être valide (6 chiffres), cf. [ValidateurCarre].
    /// - R5 (dur) : le carré reste unique pour l'utilisateur, la vérification **exclut le site
    ///   courant** (renommer un site, ou le ré-enregistrer sans changer son carré, ne doit pas
    ///   déclencher un faux conflit).
    /// - Protocole : conserve celui du site si `null`.
    /// - L'`id`, la date de création et le propriétaire sont **conservés**.
    ///
    /// @return le site mis à jour
    /// @throws IllegalArgumentException si le numéro de carré est mal formé (R1)
    /// @throws RegleMetierException si le site est introuvable, ou si le carré est déjà déclaré par
    ///     un AUTRE site de l'utilisateur (R5)
    public Site modifierSite(
            Long idSite, String numeroCarre, String nomConvivial, Protocole protocole, String commentaire) {
        ValidateurCarre.exigerValide(numeroCarre); // R1
        Site existant =
                siteDao.findById(idSite).orElseThrow(() -> new RegleMetierException("Site introuvable : " + idSite));
        boolean carreDejaPris = siteDao.findByUtilisateur(existant.idUtilisateur()).stream()
                .anyMatch(autre ->
                        autre.numeroCarre().equals(numeroCarre) && !autre.id().equals(idSite)); // R5
        if (carreDejaPris) {
            throw new RegleMetierException("Le carré " + numeroCarre + " est déjà déclaré pour cet utilisateur.");
        }
        Protocole effectif = protocole != null ? protocole : existant.protocole();
        Site aMettreAJour = new Site(
                existant.id(),
                numeroCarre,
                nomConvivial,
                effectif,
                commentaire,
                existant.dateCreation(),
                existant.idUtilisateur());
        siteDao.update(aMettreAJour);
        return aMettreAJour;
    }

    /// Ajoute un point d'écoute à un site existant.
    ///
    /// - Le site doit exister.
    /// - R2 (dur) : le code de point doit valider `[A-Z][0-9]+`, cf. [ValidateurCodePoint].
    /// - Unicité (dur) : le code doit être unique dans le site.
    ///
    /// @return le point inséré, avec son `id` auto-généré
    /// @throws RegleMetierException si le site est introuvable ou si le code existe déjà dans
    ///     le site
    /// @throws IllegalArgumentException si le code de point est mal formé (R2)
    public PointDEcoute ajouterPoint(Long idSite, String code, Double latitude, Double longitude, String description) {
        return ajouterPoint(idSite, code, latitude, longitude, description, false);
    }

    /// Ajoute un point **rapatrié** de VigieChiro (#1738), marqué `synchronise` : la fiche site pourra le
    /// masquer tant qu'aucune nuit ne s'y rattache (contrairement à un point ajouté à la main, toujours
    /// visible). Réservé au rapprochement des sites ([RapprochementSites]) ; mêmes règles (R2, unicité).
    public PointDEcoute ajouterPointSynchronise(
            Long idSite, String code, Double latitude, Double longitude, String description) {
        return ajouterPoint(idSite, code, latitude, longitude, description, true);
    }

    private PointDEcoute ajouterPoint(
            Long idSite, String code, Double latitude, Double longitude, String description, boolean synchronise) {
        Site site =
                siteDao.findById(idSite).orElseThrow(() -> new RegleMetierException("Site introuvable : " + idSite));
        ValidateurCodePoint.exigerValide(code); // R2
        boolean codeDejaPris =
                pointDao.findBySite(site.id()).stream().anyMatch(p -> p.code().equals(code));
        if (codeDejaPris) {
            throw new RegleMetierException(
                    "Le code de point « " + code + " » existe déjà dans ce site (unicité code/point).");
        }
        PointDEcoute aCreer = new PointDEcoute(null, code, latitude, longitude, description, site.id(), synchronise);
        PointDEcoute ajoute = pointDao.insert(aCreer);
        journal.mutationStructurelleValidee();
        return ajoute;
    }

    /// Met à jour un point d'écoute existant (édition depuis la modale).
    ///
    /// - R2 (dur) : le code doit valider `[A-Z][0-9]+`, cf. [ValidateurCodePoint].
    /// - Intégrité (dur) : le point doit exister et appartenir au site fourni (on refuse un couple
    ///   `idPoint`/`idSite` incohérent qui déplacerait silencieusement un point d'un site à l'autre).
    /// - Unicité (dur) : le code doit rester unique dans le site, **en excluant le point courant**
    ///   (un point conserve son propre code). Sans ce contrôle, viser un code déjà pris ferait
    ///   remonter la contrainte SQL `UNIQUE(site_id, code)` en erreur technique plutôt qu'un refus
    ///   métier lisible. Symétrique d'[#ajouterPoint] pour le chemin d'édition.
    ///
    /// @return le point mis à jour
    /// @throws IllegalArgumentException si le code est mal formé (R2)
    /// @throws RegleMetierException si le point est introuvable, n'appartient pas au site, ou si le
    ///     code est déjà pris par un AUTRE point du site
    public PointDEcoute modifierPoint(
            Long idPoint, Long idSite, String code, Double latitude, Double longitude, String description) {
        ValidateurCodePoint.exigerValide(code); // R2
        PointDEcoute existant = pointDao.findById(idPoint).orElseThrow(() -> pointIntrouvable(idPoint));
        if (!existant.idSite().equals(idSite)) {
            throw new RegleMetierException("Le point " + idPoint + " n'appartient pas au site " + idSite + ".");
        }
        boolean codeDejaPris =
                pointDao.findBySite(idSite).stream().anyMatch(p -> p.code().equals(code) && !p.id().equals(idPoint));
        if (codeDejaPris) {
            throw new RegleMetierException(
                    "Le code de point « " + code + " » existe déjà dans ce site (unicité code/point).");
        }
        PointDEcoute aMettreAJour = new PointDEcoute(idPoint, code, latitude, longitude, description, idSite);
        pointDao.update(aMettreAJour);
        if (!Objects.equals(existant.latitude(), latitude) || !Objects.equals(existant.longitude(), longitude)) {
            // Le GPS a bougé : la commune mémorisée est périmée. L'effacement est local et immédiat
            // (jamais bloquant) ; la résolution, elle, est relancée hors du fil JavaFX par les
            // déclencheurs (#2791), et à défaut, le rattrapage comblera.
            communes.effacer(idPoint);
        }
        return aMettreAJour;
    }

    /// **Déplace** un point d'écoute : met à jour ses **seules coordonnées GPS**, en préservant son code,
    /// sa description et son site. Recharge le point pour conserver ces champs, puis délègue à
    /// [#modifierPoint] (mêmes contrôles d'intégrité). Sert au glisser d'un marqueur sur la carte
    /// multi-sites (#154) : seul le GPS change, jamais le code ni le descriptif.
    ///
    /// @throws RegleMetierException si le point est introuvable
    public PointDEcoute deplacerPoint(Long idPoint, Double latitude, Double longitude) {
        PointDEcoute existant = pointDao.findById(idPoint).orElseThrow(() -> pointIntrouvable(idPoint));
        return modifierPoint(idPoint, existant.idSite(), existant.code(), latitude, longitude, existant.description());
    }

    /// Sites d'un utilisateur, triés par numéro de carré.
    public List<Site> listerSites(String idUtilisateur) {
        return siteDao.findByUtilisateur(idUtilisateur);
    }

    /// Points d'écoute d'un site, triés par code.
    public List<PointDEcoute> listerPoints(Long idSite) {
        return pointDao.findBySite(idSite);
    }

    /// Nombre total de sites (compteur du tableau de bord d'accueil).
    public long compterSites() {
        return siteDao.compter();
    }

    /// Nombre total de points d'écoute (compteur du tableau de bord d'accueil).
    public long compterPoints() {
        return pointDao.compter();
    }

    /// Supprime un site **si et seulement si** aucun passage n'est rattaché à ses points (règle
    /// dure).
    ///
    /// Le schéma cascade `monitoring_site → listening_point → passage` : sans ce garde-fou, un
    /// simple `DELETE` sur le site détruirait silencieusement les passages (et leurs sessions,
    /// séquences…) via `ON DELETE CASCADE`. Le service refuse donc tant qu'un passage existe ;
    /// une fois les passages supprimés, le `delete` ne fait disparaître que les points (sans
    /// passage) du site, par cascade.
    ///
    /// @throws RegleMetierException si au moins un point du site porte un passage
    public void supprimerSite(Long idSite) {
        List<String> bloquants = pointsPortantUnPassage(idSite);
        if (!bloquants.isEmpty()) {
            throw new RegleMetierException("Suppression refusée : le point « "
                    + bloquants.getFirst()
                    + " » porte au moins un passage. Supprimez d'abord les passages rattachés.");
        }
        siteDao.delete(idSite);
        journal.mutationStructurelleValidee();
    }

    /// Le refus commun aux trois gestes qui prennent un identifiant de point. Factorisé parce que le
    /// troisième appelant a fait mordre PMD, et qu'un message d'erreur recopié finit par diverger -
    /// c'est exactement ce qui a produit #3584.
    private static RegleMetierException pointIntrouvable(Long idPoint) {
        return new RegleMetierException("Point introuvable : " + idPoint);
    }

    /// Supprime un **point d'écoute** si aucun passage n'y est rattaché (#3584).
    ///
    /// Même règle dure que [#supprimerSite], au niveau du point : le schéma cascade
    /// `listening_point → passage`, donc un `DELETE` non gardé détruirait silencieusement les nuits du
    /// point (et leurs sessions, séquences…).
    ///
    /// Ce geste vivait dans `SiteDetailViewModel`, avec sa propre copie de la règle. C'était la seule
    /// écriture structurelle du dépôt hors service, et les deux copies avaient **déjà divergé** : l'une
    /// disait quoi faire (« Supprimez d'abord les passages rattachés »), l'autre s'arrêtait à
    /// « suppression bloquée ». La doc-comment de [#pointsPortantUnPassage] l'avait écrit noir sur
    /// blanc avant que ça n'arrive.
    ///
    /// @throws RegleMetierException si le point porte au moins un passage
    public void supprimerPoint(Long idPoint) {
        PointDEcoute point = pointDao.findById(idPoint).orElseThrow(() -> pointIntrouvable(idPoint));
        if (!passageDao.findByPoint(idPoint).isEmpty()) {
            throw new RegleMetierException("Suppression refusée : le point « "
                    + point.code()
                    + " » porte au moins un passage. Supprimez d'abord les passages rattachés.");
        }
        pointDao.delete(idPoint);
        journal.mutationStructurelleValidee();
    }

    /// Les codes des points qui **empêchent** la suppression du site, dans l'ordre des points (#1383).
    ///
    /// Lecture seule, extraite de [#supprimerSite] pour qu'une surface puisse dire **avant d'agir** que
    /// le geste sera refusé, plutôt que de le découvrir en le tentant. La ligne de commande s'en sert
    /// pour refuser d'emblée au lieu de faire confirmer une perte vouée à l'échec.
    ///
    /// Le refus lui-même reste ici : deux surfaces qui rejoueraient la règle chacune de leur côté
    /// finiraient par ne plus dire la même chose.
    ///
    /// @return les codes bloquants, vide si le site peut être supprimé
    public List<String> pointsPortantUnPassage(Long idSite) {
        return pointDao.findBySite(idSite).stream()
                .filter(point -> !passageDao.findByPoint(point.id()).isEmpty())
                .map(PointDEcoute::code)
                .toList();
    }

    /// Rappels non bloquants à présenter après création d'un site (R3, règle soft).
    ///
    /// Sur un site `PointFixeStandard`, l'application rappelle (sans bloquer) les deux passages
    /// annuels attendus. Sur un site `PointFixeRecherche`, la règle est **muette** : le résultat
    /// est conforme (aucune alerte). Démontre le patron « règle soft → [ResultatVerification] ».
    public ResultatVerification rappelsProtocole(Protocole protocole) {
        if (protocole == Protocole.STANDARD) {
            return ResultatVerification.de(
                    Alerte.soft("Site PointFixeStandard : 2 passages attendus par an - passage 1 entre le"
                            + " 15 juin et le 31 juillet, passage 2 entre le 15 août et le 30 septembre."
                            + " Rappel non bloquant."));
        }
        return ResultatVerification.ok();
    }

    private void exigerCarreUniquePourUtilisateur(String numeroCarre, String idUtilisateur) {
        boolean dejaPris = siteDao.findByUtilisateur(idUtilisateur).stream()
                .anyMatch(site -> site.numeroCarre().equals(numeroCarre));
        if (dejaPris) {
            throw new RegleMetierException("Le carré " + numeroCarre + " est déjà déclaré pour cet utilisateur.");
        }
    }
}
