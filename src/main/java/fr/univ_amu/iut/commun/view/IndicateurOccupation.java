package fr.univ_amu.iut.commun.view;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/// **Overlay d'occupation réutilisable** (#1014, socle du chantier de réactivité #793) : superpose sur
/// un écran un voile + une roue d'attente + un libellé « … en cours » pendant qu'un traitement lourd
/// s'exécute **hors du fil JavaFX** (via [ExecuteurTache]), puis en applique le résultat/l'erreur sur
/// le fil JavaFX. Généralise le patron `zoneProgression` de l'assistant d'import en un composant que
/// chaque écran réutilise sans recopier le trio « thread virtuel + `runningProperty` + overlay ».
///
/// Utilisation : le contrôleur enveloppe le contenu de son écran dans le [StackPane] renvoyé par
/// [#enrober] (ou passe un `StackPane` existant au constructeur), puis appelle [#occuper] pour chaque
/// traitement long. L'overlay **capte les clics** (le voile bloque l'interaction sous-jacente le temps
/// du traitement) et disparaît automatiquement à la fin.
///
/// Sœur de [IndicateurBlocage] (composant de vue instanciable) et de [ExecuteurTache] (la primitive
/// d'exécution, injectée pour rester synchrone en test).
public final class IndicateurOccupation {

    private final ExecuteurTache executeur;
    private final StackPane hote;
    private final StackPane voile;
    private final ReadOnlyBooleanWrapper enCours = new ReadOnlyBooleanWrapper(this, "enCours", false);
    private final ReadOnlyStringWrapper libelle = new ReadOnlyStringWrapper(this, "libelle", "");

    /// Prépare un overlay au-dessus du contenu de `hote` (un [StackPane] : ses enfants sont le contenu
    /// de l'écran, l'overlay se superpose par-dessus). `executeur` exécute le travail hors du fil
    /// JavaFX (injecté : synchrone en test).
    public IndicateurOccupation(StackPane hote, ExecuteurTache executeur) {
        this.hote = Objects.requireNonNull(hote, "hote");
        this.executeur = Objects.requireNonNull(executeur, "executeur");
        this.voile = construireVoile();
        hote.getChildren().add(voile);
    }

    /// Enveloppe `contenu` dans un [StackPane] et prépare l'overlay par-dessus, pour un écran dont la
    /// racine n'est pas déjà un `StackPane`. Renvoie l'indicateur ; sa racine à insérer dans la scène
    /// est accessible via [#racine].
    public static IndicateurOccupation enrober(javafx.scene.Node contenu, ExecuteurTache executeur) {
        return new IndicateurOccupation(new StackPane(Objects.requireNonNull(contenu, "contenu")), executeur);
    }

    /// Racine à placer dans la scène (le `StackPane` hôte : contenu + overlay).
    public StackPane racine() {
        return hote;
    }

    /// Exécute `travail` hors du fil JavaFX en affichant l'overlay `libelle` (« Import en cours… »),
    /// puis, sur le fil JavaFX, masque l'overlay et remet le résultat à `succes` (ou l'erreur à `echec`,
    /// à router vers le filet d'erreurs de l'écran #795). Exactement l'un des deux callbacks est appelé.
    public <T> void occuper(String libelle, Supplier<T> travail, Consumer<T> succes, Consumer<Throwable> echec) {
        Objects.requireNonNull(travail, "travail");
        Objects.requireNonNull(succes, "succes");
        Objects.requireNonNull(echec, "echec");
        this.libelle.set(libelle == null ? "" : libelle);
        enCours.set(true);
        executeur.executer(
                travail,
                resultat -> {
                    enCours.set(false);
                    succes.accept(resultat);
                },
                erreur -> {
                    enCours.set(false);
                    echec.accept(erreur);
                });
    }

    /// `true` tant qu'un traitement est en cours (l'overlay est alors visible). Observable, par exemple
    /// pour désactiver des actions pendant l'occupation.
    public ReadOnlyBooleanProperty enCoursProperty() {
        return enCours.getReadOnlyProperty();
    }

    /// Libellé affiché sous la roue d'attente pendant l'occupation.
    public ReadOnlyStringProperty libelleProperty() {
        return libelle.getReadOnlyProperty();
    }

    private StackPane construireVoile() {
        ProgressIndicator roue = new ProgressIndicator();
        roue.getStyleClass().add("occupation-roue");
        Label texte = new Label();
        texte.getStyleClass().add("occupation-libelle");
        texte.textProperty().bind(libelle);
        texte.visibleProperty().bind(libelle.isNotEmpty());
        texte.managedProperty().bind(libelle.isNotEmpty());

        VBox carte = new VBox(roue, texte);
        carte.getStyleClass().add("occupation-carte");
        carte.setAlignment(Pos.CENTER);

        StackPane voileOverlay = new StackPane(carte);
        voileOverlay.getStyleClass().add("occupation-voile");
        StackPane.setAlignment(carte, Pos.CENTER);
        // Overlay masqué au repos ; visible pendant l'occupation. `managed` suit `visible` pour ne pas
        // capter les clics quand il est masqué (un voile invisible mais managé bloquerait l'écran).
        voileOverlay.visibleProperty().bind(enCours);
        voileOverlay.managedProperty().bind(enCours);
        return voileOverlay;
    }
}
