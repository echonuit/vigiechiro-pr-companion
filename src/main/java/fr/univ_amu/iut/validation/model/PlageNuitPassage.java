package fr.univ_amu.iut.validation.model;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.CoordonneesPoint;
import fr.univ_amu.iut.commun.model.EphemerideSolaire;
import fr.univ_amu.iut.commun.model.PlageNuit;
import fr.univ_amu.iut.commun.model.PositionGeo;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;

/// Calcule la **plage nuit par défaut** du filtre « Heure » de la vue audio pour un passage (#549) :
/// heures pleines du **coucher** et du **lever** du soleil au point d'écoute, la nuit de la relève,
/// via l'[EphemerideSolaire] (calcul local, sans réseau).
///
/// Extrait de `ServiceValidation` pour ne pas alourdir ce service (responsabilité distincte). Croise
/// la date de la relève (feature `passage`, [PassageDao]) et les coordonnées du point (port socle
/// [CoordonneesPoint], implémenté par `sites` : pas de dépendance directe `validation → sites`).
public class PlageNuitPassage {

    /// Fuseau des horaires (programme national français) : le calcul solaire est en UTC, on repasse en
    /// heure locale pour donner des heures pleines cohérentes avec la saisie.
    private static final ZoneId FUSEAU_SITE = ZoneId.of("Europe/Paris");

    private final PassageDao passageDao;
    private final CoordonneesPoint coordonnees;

    @Inject
    public PlageNuitPassage(PassageDao passageDao, CoordonneesPoint coordonnees) {
        this.passageDao = Objects.requireNonNull(passageDao, "passageDao");
        this.coordonnees = Objects.requireNonNull(coordonnees, "coordonnees");
    }

    /// Plage nuit par défaut pour le passage `idPassage`, ou **vide** si le passage est introuvable,
    /// sans date, sans coordonnées de point, ou en jour/nuit polaire : l'appelant retombe alors sur le
    /// défaut fixe 21 h → 6 h.
    public Optional<PlageNuit> pour(Long idPassage) {
        Objects.requireNonNull(idPassage, "idPassage");
        Passage passage = passageDao.findById(idPassage).orElse(null);
        if (passage == null || passage.dateEnregistrement() == null) {
            return Optional.empty();
        }
        Optional<PositionGeo> position = coordonnees.pour(passage.idPoint());
        if (position.isEmpty()) {
            return Optional.empty();
        }
        PositionGeo point = position.orElseThrow();
        try {
            LocalDate nuit = LocalDate.parse(passage.dateEnregistrement());
            Optional<LocalTime> coucher =
                    EphemerideSolaire.coucherLocal(point.latitude(), point.longitude(), nuit, FUSEAU_SITE);
            Optional<LocalTime> lever =
                    EphemerideSolaire.leverLocal(point.latitude(), point.longitude(), nuit, FUSEAU_SITE);
            if (coucher.isEmpty() || lever.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new PlageNuit(
                    coucher.orElseThrow().getHour(), lever.orElseThrow().getHour()));
        } catch (DateTimeParseException dateInvalide) {
            return Optional.empty();
        }
    }
}
