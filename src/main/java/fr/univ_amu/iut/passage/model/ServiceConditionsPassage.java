package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.model.CoordonneesPoint;
import fr.univ_amu.iut.commun.model.PositionGeo;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.MaterielMicroDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;

/// Service des **conditions de la nuit** d'un passage (#1192) : relevé météo (#106/#697) et matériel du
/// micro déployé (#543). Extrait de [ServicePassage] (qui garde lecture/détail, création, règles de
/// protocole et workflow) : ces saisies optionnelles de la modale « Modifier le passage »
/// (`SaisiePassageConditions`) forment une responsabilité à part, avec leurs propres dépendances
/// ([MaterielMicroDao], [CoordonneesPoint], [FournisseurMeteo]).
///
/// Même patron que le service d'origine : pure Java sans JavaFX, dépendances par constructeur,
/// données **jamais bloquantes** (une météo absente n'empêche rien).
public class ServiceConditionsPassage {

    private final PassageDao passageDao;
    private final MaterielMicroDao materielDao;

    /// Pour garantir la ligne `recorder` avant d'y accrocher le passage : `recorder_id` est une clé
    /// étrangère **non nulle** (#1828).
    private final EnregistreurDao enregistreurDao;

    private final CoordonneesPoint coordonnees;
    private final FournisseurMeteo fournisseurMeteo;

    /// Ce que les enregistrements de la nuit prouvent de ses horaires (#1878) : quand ils parlent, la
    /// saisie n'a pas lieu d'être.
    private final FenetreObserveeNuit fenetreObservee;

    public ServiceConditionsPassage(
            PassageDao passageDao,
            MaterielMicroDao materielDao,
            EnregistreurDao enregistreurDao,
            CoordonneesPoint coordonnees,
            FournisseurMeteo fournisseurMeteo,
            FenetreObserveeNuit fenetreObservee) {
        this.passageDao = Objects.requireNonNull(passageDao, "passageDao");
        this.materielDao = Objects.requireNonNull(materielDao, "materielDao");
        this.enregistreurDao = Objects.requireNonNull(enregistreurDao, "enregistreurDao");
        this.coordonnees = Objects.requireNonNull(coordonnees, "coordonnees");
        this.fournisseurMeteo = Objects.requireNonNull(fournisseurMeteo, "fournisseurMeteo");
        this.fenetreObservee = Objects.requireNonNull(fenetreObservee, "fenetreObservee");
    }

    /// Désigne l'**enregistreur** d'un passage (#1828) : le n° de série que l'utilisateur a saisi ou choisi
    /// dans la modale, quand ni l'import ni la plateforme ne l'ont fourni. La ligne `recorder` est créée si
    /// elle manque (clé étrangère `NOT NULL`), sans écraser les métadonnées d'un enregistreur déjà connu.
    ///
    /// Le champ ne peut pas être **vidé** : le schéma exige un enregistreur. Un numéro blanc laisse donc le
    /// passage inchangé plutôt que de violer la contrainte.
    ///
    /// @param idPassage passage cible
    /// @param numeroSerie n° de série saisi (ignoré s'il est blanc)
    /// @return le passage mis à jour, ou l'existant si rien n'a changé
    public Passage definirEnregistreur(Long idPassage, String numeroSerie) {
        Passage passage = charger(idPassage);
        if (numeroSerie == null || numeroSerie.isBlank()) {
            return passage;
        }
        String serie = numeroSerie.trim();
        if (serie.equals(passage.idEnregistreur())) {
            return passage;
        }
        if (enregistreurDao.findById(serie).isEmpty()) {
            enregistreurDao.insert(new Enregistreur(serie, null, null));
        }
        Passage modifie = avecEnregistreur(passage, serie);
        passageDao.update(modifie);
        return modifie;
    }

    /// Copie `passage` en ne changeant que son enregistreur (colonne `recorder_id`).
    private static Passage avecEnregistreur(Passage passage, String idEnregistreur) {
        return new Passage(
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
                idEnregistreur);
    }

    /// Renseigne le **relevé météo complet** d'un passage (température début/fin, vent, couverture
    /// nuageuse ; #106 étendu) : données **optionnelles** stockées dans `passage.weather_data` via
    /// [MeteoPassage]. Chaque grandeur `null` du relevé efface sa clé ; les autres clés sont préservées.
    ///
    /// @param idPassage passage cible
    /// @param releve relevé météo (grandeurs optionnelles ; `null` par grandeur = effacer)
    /// @return le passage mis à jour
    public Passage definirMeteo(Long idPassage, MeteoReleve releve) {
        Passage passage = charger(idPassage);
        Passage modifie = avecDonneesMeteo(passage, MeteoPassage.definirReleve(passage.donneesMeteo(), releve));
        passageDao.update(modifie);
        return modifie;
    }

    /// Copie `passage` en ne changeant que ses données météo sérialisées (colonne `weather_data`).
    private static Passage avecDonneesMeteo(Passage passage, String donneesMeteo) {
        return new Passage(
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
                donneesMeteo,
                passage.deposeLe(),
                passage.idPoint(),
                passage.idEnregistreur());
    }

    /// Matériel du micro déployé pour le passage `idPassage` (position sol/canopée, hauteur de fixation,
    /// type ; métadonnées de dépôt VigieChiro). Renvoie un [MaterielMicro#vide] si rien n'a été saisi —
    /// jamais `null`. Le n° de série du détecteur n'est pas ici (il vit sur l'enregistreur du passage).
    public MaterielMicro materiel(Long idPassage) {
        Objects.requireNonNull(idPassage, "idPassage");
        return materielDao.pour(idPassage);
    }

    /// Enregistre le **matériel du micro** d'un passage (ou **efface** la ligne si le relevé est vide),
    /// dans la table `passage_equipment`. Le passage lui-même n'est pas modifié. Le `materiel` porte son
    /// `idPassage`.
    ///
    /// @param materiel matériel saisi (grandeurs optionnelles), rattaché à son passage
    public void definirMateriel(MaterielMicro materiel) {
        Objects.requireNonNull(materiel, "materiel");
        materielDao.definir(materiel);
    }

    /// Tente de **récupérer la météo** de la nuit d'un passage via le [FournisseurMeteo] (Open-Meteo),
    /// pour pré-remplir le relevé : au **GPS du point** (obtenu via le port socle [CoordonneesPoint],
    /// implémenté par `sites`, pour éviter un cycle passage ↔ sites) et aux **heures de début/fin**
    /// du passage. **Jamais bloquant** : [Optional#empty()] si le point n'a pas de GPS, si les
    /// horodatages sont illisibles, ou si le service est indisponible (hors-ligne).
    ///
    /// ⚠️ **Opération réseau** : à appeler **hors du fil JavaFX** (l'IHM la lance en tâche de fond).
    ///
    /// @param idPassage passage cible
    /// @return le relevé météo récupéré (grandeurs éventuellement partielles), ou vide
    public Optional<MeteoReleve> recupererMeteo(Long idPassage) {
        Passage passage = charger(idPassage);
        Optional<PositionGeo> position = coordonnees.pour(passage.idPoint());
        if (position.isEmpty()) {
            return Optional.empty();
        }
        PositionGeo point = position.get();
        try {
            return fournisseurMeteo.pour(
                    point.latitude(),
                    point.longitude(),
                    LocalDate.parse(passage.dateEnregistrement()),
                    LocalTime.parse(passage.heureDebut()),
                    LocalTime.parse(passage.heureFin()));
        } catch (DateTimeParseException horodatageInvalide) {
            return Optional.empty();
        }
    }

    private Passage charger(Long idPassage) {
        Objects.requireNonNull(idPassage, "idPassage");
        return passageDao
                .findById(idPassage)
                .orElseThrow(() -> new RegleMetierException("Passage introuvable : " + idPassage));
    }

    /// Corrige les **heures de la nuit** d'un passage (#1892).
    ///
    /// L'application dérive normalement cette fenêtre de la machine : du journal du capteur à l'import,
    /// ou des enregistrements eux-mêmes (#1878). C'est le bon défaut - une mesure vaut mieux qu'une
    /// saisie. Mais une nuit **rapatriée en squelette** n'a aucune preuve locale, et jusqu'ici aucun
    /// recours n'existait : ses heures, une fois fausses, l'étaient à vie. La règle se complète donc :
    /// les heures viennent des **preuves** quand elles existent, de l'**utilisateur** sinon.
    ///
    /// Deux règles de validation, et une seule interdiction :
    ///  - une fin **antérieure** au début est **normale** : une nuit est à cheval sur minuit, et le mapping
    ///    VigieChiro le sait déjà (`CorrespondanceParticipation`) ;
    ///  - une fin **égale** au début est refusée : elle ne délimite aucune nuit, et l'envoi la prendrait
    ///    pour une nuit de 24 h - le mécanisme observé dans #1860.
    ///
    /// @param idPassage passage cible
    /// @param heureDebut heure de début, format `HH:mm` ou `HH:mm:ss`
    /// @param heureFin heure de fin (peut précéder le début)
    /// @return le passage mis à jour
    /// @throws RegleMetierException si une heure est illisible, ou si la fin égale le début
    public Passage definirHoraires(Long idPassage, String heureDebut, String heureFin) {
        Passage passage = charger(idPassage);
        if (heuresProuvees(idPassage)) {
            // Accepter la saisie serait la trahir : le premier envoi la remplacerait par les preuves
            // (#1878). Mieux vaut refuser en le disant que faire semblant d'obéir.
            throw new RegleMetierException("Les heures de cette nuit sont établies par ses enregistrements :"
                    + " elles ne se saisissent pas. Elles se réalignent d'elles-mêmes à l'envoi.");
        }
        LocalTime debut = heureExigee(heureDebut, "de début");
        LocalTime fin = heureExigee(heureFin, "de fin");
        if (debut.equals(fin)) {
            throw new RegleMetierException("L'heure de fin ne peut pas être identique à l'heure de début :"
                    + " la nuit ne durerait rien, et serait déposée comme une nuit de 24 heures.");
        }
        Passage modifie = avecHoraires(passage, debut.toString(), fin.toString());
        passageDao.update(modifie);
        return modifie;
    }

    /// `true` si les enregistrements de la nuit **attestent** ses horaires : la saisie est alors sans
    /// objet (et serait écrasée au premier envoi, #1878). Faux pour une nuit squelette, qui n'a rien pour
    /// se prouver - c'est précisément le cas où l'utilisateur doit pouvoir corriger.
    public boolean heuresProuvees(Long idPassage) {
        return fenetreObservee.pour(idPassage).isPresent();
    }

    /// Heure saisie, ou refus nommant **laquelle** des deux bornes est en cause.
    private static LocalTime heureExigee(String saisie, String borne) {
        if (saisie == null || saisie.isBlank()) {
            throw new RegleMetierException("L'heure " + borne + " est obligatoire (format 21:00).");
        }
        try {
            return LocalTime.parse(saisie.trim());
        } catch (DateTimeParseException illisible) {
            throw new RegleMetierException(
                    "L'heure " + borne + " est illisible : « " + saisie.trim() + " » (format attendu 21:00).");
        }
    }

    /// Copie `passage` en ne changeant que ses heures de nuit.
    private static Passage avecHoraires(Passage passage, String heureDebut, String heureFin) {
        return new Passage(
                passage.id(),
                passage.numeroPassage(),
                passage.annee(),
                passage.dateEnregistrement(),
                heureDebut,
                heureFin,
                passage.parametresAcquisition(),
                passage.statutWorkflow(),
                passage.verdictVerification(),
                passage.commentaire(),
                passage.donneesMeteo(),
                passage.deposeLe(),
                passage.idPoint(),
                passage.idEnregistreur());
    }
}
