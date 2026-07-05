package fr.univ_amu.iut.passage.viewmodel;

import fr.univ_amu.iut.passage.model.MaterielMicro;
import fr.univ_amu.iut.passage.model.MeteoPassage;
import fr.univ_amu.iut.passage.model.MeteoReleve;
import fr.univ_amu.iut.passage.model.PositionMicro;
import fr.univ_amu.iut.passage.model.ServicePassage;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/// Saisie des **conditions de dépôt** d'un passage (métadonnées demandées par VigieChiro), extraite de
/// [PassageViewModel] pour garder chaque ViewModel focalisé :
///
/// - le **relevé météo** (température début/fin, vent, couverture nuageuse ; #106 étendu), enregistré
///   d'un bloc via [ServicePassage#definirMeteo] ;
/// - le **matériel du micro** (position sol/canopée, hauteur de fixation, type), enregistré d'un bloc via
///   [ServicePassage#definirMateriel].
///
/// Chaque grandeur est **optionnelle** (vide = effacer). Une saisie non numérique publie un message
/// d'erreur **partagé** avec l'écran (la même ligne de message que [PassageViewModel]), sans rien
/// modifier. VM agnostique de l'IHM (`javafx.beans` uniquement).
public class SaisiePassageConditions {

    private final ServicePassage service;
    private final ReadOnlyStringWrapper message;
    private Long idPassage;

    private final StringProperty temperatureSaisie = new SimpleStringProperty(this, "temperatureSaisie", "");
    private final StringProperty temperatureFinSaisie = new SimpleStringProperty(this, "temperatureFinSaisie", "");
    private final StringProperty ventSaisie = new SimpleStringProperty(this, "ventSaisie", "");
    private final StringProperty couvertureNuageuseSaisie =
            new SimpleStringProperty(this, "couvertureNuageuseSaisie", "");

    private final ObjectProperty<PositionMicro> positionSaisie = new SimpleObjectProperty<>(this, "positionSaisie");
    private final StringProperty hauteurSaisie = new SimpleStringProperty(this, "hauteurSaisie", "");
    private final StringProperty typeMicroSaisie = new SimpleStringProperty(this, "typeMicroSaisie", "");

    SaisiePassageConditions(ServicePassage service, ReadOnlyStringWrapper message) {
        this.service = service;
        this.message = message;
    }

    /// Charge les conditions du passage `idPassage` : le relevé `meteo` (issu de la projection) et le
    /// matériel (relu du service). Tolérant : un relevé/matériel nul donne des champs vides.
    void charger(Long idPassage, MeteoReleve meteo) {
        this.idPassage = idPassage;
        appliquerMeteo(meteo);
        appliquerMateriel(service.materiel(idPassage));
    }

    /// Vide tous les champs (écran réinitialisé).
    void reinitialiser() {
        appliquerMeteo(MeteoReleve.VIDE);
        appliquerMateriel(null);
    }

    /// Enregistre le **relevé météo** saisi (grandeur vide = effacer ; saisie non numérique = message
    /// d'erreur, sans modification), puis renormalise les champs.
    public void enregistrerMeteo() {
        try {
            MeteoReleve releve = new MeteoReleve(
                    MeteoPassage.lireSaisie(temperatureSaisie.get()),
                    MeteoPassage.lireSaisie(temperatureFinSaisie.get()),
                    MeteoPassage.lireSaisie(ventSaisie.get()),
                    MeteoPassage.lireSaisie(couvertureNuageuseSaisie.get()));
            service.definirMeteo(idPassage, releve);
            appliquerMeteo(releve);
            message.set("");
        } catch (NumberFormatException invalide) {
            message.set("Valeur météo invalide : saisissez des nombres, ou laissez vide.");
        }
    }

    /// Enregistre le **matériel du micro** saisi (grandeur vide = effacer ; hauteur non numérique =
    /// message d'erreur, sans modification), puis renormalise les champs.
    public void enregistrerMateriel() {
        try {
            MaterielMicro materiel = new MaterielMicro(
                    idPassage,
                    positionSaisie.get(),
                    MeteoPassage.lireSaisie(hauteurSaisie.get()),
                    texteOuNull(typeMicroSaisie.get()));
            service.definirMateriel(materiel);
            appliquerMateriel(materiel);
            message.set("");
        } catch (NumberFormatException invalide) {
            message.set("Hauteur invalide : saisissez un nombre (m), ou laissez vide.");
        }
    }

    private void appliquerMeteo(MeteoReleve releve) {
        MeteoReleve valeurs = releve == null ? MeteoReleve.VIDE : releve;
        temperatureSaisie.set(texteOuVide(valeurs.temperatureDebutNuit()));
        temperatureFinSaisie.set(texteOuVide(valeurs.temperatureFinNuit()));
        ventSaisie.set(texteOuVide(valeurs.vent()));
        couvertureNuageuseSaisie.set(texteOuVide(valeurs.couvertureNuageuse()));
    }

    private void appliquerMateriel(MaterielMicro materiel) {
        positionSaisie.set(materiel == null ? null : materiel.positionMicro());
        hauteurSaisie.set(materiel == null ? "" : texteOuVide(materiel.hauteurMetres()));
        typeMicroSaisie.set(materiel == null || materiel.typeMicro() == null ? "" : materiel.typeMicro());
    }

    private static String texteOuVide(Double valeur) {
        return valeur == null ? "" : valeur.toString();
    }

    private static String texteOuNull(String saisie) {
        return saisie == null || saisie.isBlank() ? null : saisie.trim();
    }

    public StringProperty temperatureSaisieProperty() {
        return temperatureSaisie;
    }

    public StringProperty temperatureFinSaisieProperty() {
        return temperatureFinSaisie;
    }

    public StringProperty ventSaisieProperty() {
        return ventSaisie;
    }

    public StringProperty couvertureNuageuseSaisieProperty() {
        return couvertureNuageuseSaisie;
    }

    public ObjectProperty<PositionMicro> positionSaisieProperty() {
        return positionSaisie;
    }

    public StringProperty hauteurSaisieProperty() {
        return hauteurSaisie;
    }

    public StringProperty typeMicroSaisieProperty() {
        return typeMicroSaisie;
    }
}
