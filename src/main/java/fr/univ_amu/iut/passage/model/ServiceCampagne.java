package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.passage.model.dao.CampagneDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// Service métier de la feature **campagne** (#2355) : créer, renommer et supprimer une campagne, en
/// lister. La campagne est un regroupement **facultatif** de passages ; ce service ne connaît pas les
/// passages, il gère l'objet lui-même. Le lien passage↔campagne (et le fait que supprimer une
/// campagne **détache** ses passages plutôt que de les effacer) est porté par la colonne nullable
/// `passage.campaign_id` en `ON DELETE SET NULL` (lot 1 PR 2).
///
/// Constructeur **simple** (sans annotation d'injection), assemblé par
/// [fr.univ_amu.iut.passage.di.CampagneModule]. Dates via l'[Horloge] injectée (année par défaut
/// déterministe en test).
public class ServiceCampagne {

    private final CampagneDao campagneDao;
    private final PassageDao passageDao;
    private final Horloge horloge;

    public ServiceCampagne(CampagneDao campagneDao, PassageDao passageDao, Horloge horloge) {
        this.campagneDao = Objects.requireNonNull(campagneDao, "campagneDao");
        this.passageDao = Objects.requireNonNull(passageDao, "passageDao");
        this.horloge = Objects.requireNonNull(horloge, "horloge");
    }

    /// Crée une campagne. Le nom est obligatoire (non vide) ; l'année vaut celle de l'[Horloge] si
    /// `annee` est `null`.
    ///
    /// @return la campagne insérée, avec son `id` auto-généré
    /// @throws IllegalArgumentException si le nom est vide
    public Campagne creerCampagne(String nom, Integer annee, String commentaire) {
        String nomNettoye = exigerNom(nom);
        int anneeEffective = annee != null ? annee : horloge.aujourdhui().getYear();
        return campagneDao.insert(new Campagne(null, nomNettoye, anneeEffective, commentaire));
    }

    /// Modifie une campagne existante (renommer, changer l'année ou le commentaire).
    ///
    /// @return la campagne mise à jour
    /// @throws IllegalArgumentException si le nom est vide
    /// @throws RegleMetierException si la campagne est introuvable
    public Campagne modifierCampagne(Long idCampagne, String nom, int annee, String commentaire) {
        charger(idCampagne);
        Campagne aMettreAJour = new Campagne(idCampagne, exigerNom(nom), annee, commentaire);
        campagneDao.update(aMettreAJour);
        return aMettreAJour;
    }

    /// Supprime une campagne. Les passages qui y étaient rattachés sont **détachés** (leur
    /// `campaign_id` repasse à `null` par la clé étrangère `ON DELETE SET NULL`), jamais effacés.
    ///
    /// @throws RegleMetierException si la campagne est introuvable
    public void supprimerCampagne(Long idCampagne) {
        charger(idCampagne);
        campagneDao.delete(idCampagne);
    }

    /// Rattache un passage à une campagne, ou l'en **détache** si `idCampagne` est `null`. Le
    /// rattachement est un simple lien facultatif : rien d'autre du passage n'est modifié.
    ///
    /// @return le passage mis à jour (persisté)
    /// @throws RegleMetierException si le passage, ou la campagne visée, est introuvable
    public Passage rattacherPassage(Long idPassage, Long idCampagne) {
        Objects.requireNonNull(idPassage, "idPassage");
        if (idCampagne != null) {
            charger(idCampagne); // la campagne visée doit exister
        }
        Passage passage = passageDao
                .findById(idPassage)
                .orElseThrow(() -> new RegleMetierException("Passage introuvable : " + idPassage));
        Passage misAJour = new Passage(
                passage.id(),
                passage.numeroPassage(),
                passage.annee(),
                passage.dateEnregistrement(),
                passage.heureDebut(),
                passage.heureFin(),
                passage.parametresAcquisition(),
                passage.statutWorkflow(),
                passage.verdictVerification(),
                passage.commentaire(),
                passage.donneesMeteo(),
                passage.deposeLe(),
                passage.idPoint(),
                passage.idEnregistreur(),
                idCampagne);
        passageDao.update(misAJour);
        return misAJour;
    }

    /// Détache un passage de sa campagne (raccourci de [rattacherPassage] avec `null`).
    public Passage detacherPassage(Long idPassage) {
        return rattacherPassage(idPassage, null);
    }

    /// Toutes les campagnes, de la plus récente à la plus ancienne.
    public List<Campagne> listerCampagnes() {
        return campagneDao.toutes();
    }

    /// Année proposée par défaut à la création (celle de l'[Horloge] injectée).
    ///
    /// Exposée pour que la surface ne rappelle pas `LocalDate.now()` de son côté : une capture ou un
    /// test qui repart d'une horloge figée doit voir l'année figée, pas celle de la machine.
    public int anneeParDefaut() {
        return horloge.aujourdhui().getYear();
    }

    /// Nombre de campagnes (compteur éventuel d'accueil).
    public long compterCampagnes() {
        return campagneDao.compter();
    }

    /// Nombre de passages **rattachés** à une campagne (#2630).
    ///
    /// Sert à la confirmation de suppression : « 12 passages seront détachés ». Une suppression qui
    /// annonce l'ampleur de son effet se décide ; une qui ne dit rien se subit. La campagne n'a pas
    /// besoin d'exister : une campagne inconnue n'a simplement aucun passage.
    public long compterPassagesRattaches(Long idCampagne) {
        Objects.requireNonNull(idCampagne, "idCampagne");
        return passageDao.compterParCampagne(idCampagne);
    }

    /// Campagne du **dernier passage** de ce point (#2631), ou [Optional#empty()] si aucun n'en porte.
    ///
    /// Sert à *proposer* un rattachement à l'import, jamais à l'imposer : c'est le suivi qui porte la
    /// campagne, et deux nuits d'affilée sur un même point en relèvent presque toujours. L'absence de
    /// proposition est un cas **normal**, pas un défaut à signaler.
    public Optional<Campagne> derniereCampagneDuPoint(Long idPoint) {
        Objects.requireNonNull(idPoint, "idPoint");
        return passageDao.dernierAvecCampagne(idPoint).map(Passage::idCampagne).flatMap(campagneDao::findById);
    }

    /// La campagne à laquelle un passage est rattaché, ou [Optional#empty()] s'il ne l'est pas (ou si
    /// le passage est introuvable). Sert à pré-sélectionner la modale « Modifier le passage ».
    public Optional<Campagne> campagneDePassage(Long idPassage) {
        return passageDao
                .findById(idPassage)
                .map(Passage::idCampagne)
                .filter(Objects::nonNull)
                .flatMap(campagneDao::findById);
    }

    private Campagne charger(Long idCampagne) {
        Objects.requireNonNull(idCampagne, "idCampagne");
        return campagneDao
                .findById(idCampagne)
                .orElseThrow(() -> new RegleMetierException("Campagne introuvable : " + idCampagne));
    }

    private static String exigerNom(String nom) {
        if (nom == null || nom.isBlank()) {
            throw new IllegalArgumentException("Le nom de la campagne est obligatoire.");
        }
        return nom.strip();
    }
}
