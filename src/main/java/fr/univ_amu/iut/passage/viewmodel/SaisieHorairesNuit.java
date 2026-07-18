package fr.univ_amu.iut.passage.viewmodel;

import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.passage.model.ServiceConditionsPassage;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/// Saisie des **heures de la nuit** dans la modale « Modifier le passage » (#1892).
///
/// Préoccupation distincte des conditions de dépôt (météo, micro, enregistreur), qui décrivent ce que
/// l'observateur a **relevé** : les heures, elles, délimitent la nuit. Elles ont surtout une règle qui
/// n'appartient qu'à elles - **on ne les saisit que si rien ne les prouve**.
///
/// Quand la nuit porte des enregistrements, ce sont eux qui font autorité (#1878) et les champs sont en
/// lecture seule : accepter une saisie serait la trahir, puisque le premier envoi la remplacerait.
///
/// Partage la ligne de message de la modale, comme [SaisiePassageConditions].
public class SaisieHorairesNuit {

    private final ServiceConditionsPassage service;
    private final ReadOnlyStringWrapper message;

    private final StringProperty debut = new SimpleStringProperty(this, "debut", "");
    private final StringProperty fin = new SimpleStringProperty(this, "fin", "");
    private final ReadOnlyBooleanWrapper prouvees = new ReadOnlyBooleanWrapper(this, "prouvees");

    /// Heures telles qu'elles ont été chargées : sert à n'écrire que si la saisie a bougé.
    private String chargees = "";

    private Long idPassage;

    SaisieHorairesNuit(ServiceConditionsPassage service, ReadOnlyStringWrapper message) {
        this.service = service;
        this.message = message;
    }

    /// Charge les heures du passage et détermine si ses enregistrements les attestent.
    void charger(Long idPassage, String heureDebut, String heureFin) {
        this.idPassage = idPassage;
        debut.set(sansSecondes(heureDebut));
        fin.set(sansSecondes(heureFin));
        chargees = couple();
        prouvees.set(service.heuresProuvees(idPassage));
    }

    /// Heure affichée au format du champ (`21:00`), quelle que soit sa forme en base.
    ///
    /// Les nuits importées portent `HH:mm:ss`, celles saisies ici `HH:mm` (le service normalise à
    /// l'écriture). Sans cette mise en forme, deux nuits voisines s'affichaient différemment - et un
    /// « 20:25:00 » démentait le `ex. 21:00` du champ juste à côté.
    private static String sansSecondes(String heure) {
        if (heure == null || heure.isBlank()) {
            return "";
        }
        try {
            return LocalTime.parse(heure.trim()).toString();
        } catch (DateTimeParseException illisible) {
            // Une heure qu'on ne sait pas lire est montrée telle quelle : la masquer priverait
            // l'utilisateur du seul indice lui disant ce qu'il y a à corriger.
            return heure.trim();
        }
    }

    /// Enregistre les heures saisies, **si** elles sont saisissables et ont effectivement changé.
    ///
    /// Renvoie `false` en nommant la cause si une borne est illisible ou si la fin égale le début.
    public boolean enregistrer() {
        // Rien à faire si la nuit se prouve elle-même, ou si la saisie n'a pas bougé : « Appliquer » ne
        // doit pas réécrire des heures que personne n'a touchées.
        if (prouvees.get() || couple().equals(chargees)) {
            return true;
        }
        try {
            service.definirHoraires(idPassage, debut.get(), fin.get());
            chargees = couple();
            message.set("");
            return true;
        } catch (RegleMetierException refus) {
            message.set(refus.getMessage());
            return false;
        }
    }

    public StringProperty debutProperty() {
        return debut;
    }

    public StringProperty finProperty() {
        return fin;
    }

    /// `true` si les enregistrements de la nuit attestent ses horaires : les champs sont alors en lecture
    /// seule, et la vue en dit le motif.
    public ReadOnlyBooleanProperty prouveesProperty() {
        return prouvees.getReadOnlyProperty();
    }

    /// Les deux heures saisies, sous une forme comparable.
    private String couple() {
        return debut.get() + "/" + fin.get();
    }
}
