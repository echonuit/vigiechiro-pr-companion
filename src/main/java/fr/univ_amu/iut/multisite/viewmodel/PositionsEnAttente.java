package fr.univ_amu.iut.multisite.viewmodel;

import fr.univ_amu.iut.sites.model.ServiceSites;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;

/// **File des déplacements de points en attente d'enregistrement** (mode édition des positions de la carte
/// multi-sites, #154). On n'écrit rien au fil de l'eau : chaque déplacement (`idPoint → {lat, lon}`)
/// s'accumule, puis l'utilisateur **enregistre** (un [ServiceSites#deplacerPoint] par point, code et
/// description préservés) ou **abandonne**.
///
/// Objet de **présentation** (couche `viewmodel`) : il ne connaît que le service métier et deux rappels
/// fournis par le ViewModel hôte (recharger la carte ; rapporter un motif d'échec). Extrait du
/// [MultisiteViewModel] pour garder chaque responsabilité petite et testable.
public class PositionsEnAttente {

    private final ServiceSites serviceSites;

    /// Rechargement de la carte (à jouer après un enregistrement ou un abandon, pour refléter l'état réel).
    private final Runnable rechargerCarte;

    /// Rapport d'un motif d'échec (typiquement vers le message d'erreur du ViewModel).
    private final Consumer<String> rapporterErreur;

    /// `idPoint → {latitude, longitude}`. `LinkedHashMap` : ordre d'écriture stable (déterminisme).
    private final Map<Long, double[]> file = new LinkedHashMap<>();

    private final ReadOnlyBooleanWrapper modifiees = new ReadOnlyBooleanWrapper(this, "modifiees", false);

    public PositionsEnAttente(ServiceSites serviceSites, Runnable rechargerCarte, Consumer<String> rapporterErreur) {
        this.serviceSites = Objects.requireNonNull(serviceSites, "serviceSites");
        this.rechargerCarte = Objects.requireNonNull(rechargerCarte, "rechargerCarte");
        this.rapporterErreur = Objects.requireNonNull(rapporterErreur, "rapporterErreur");
    }

    /// Met **en attente** le déplacement d'un point (un nouveau déplacement du même point écrase le
    /// précédent), et active l'état « modifications en attente ».
    public void deplacer(Long idPoint, double latitude, double longitude) {
        Objects.requireNonNull(idPoint, "idPoint");
        file.put(idPoint, new double[] {latitude, longitude});
        modifiees.set(true);
    }

    /// Vrai s'il existe au moins un déplacement en attente (pilote le bouton « Enregistrer » et l'alerte
    /// de sortie d'édition).
    public ReadOnlyBooleanProperty modifieesProperty() {
        return modifiees.getReadOnlyProperty();
    }

    /// Y a-t-il des déplacements non enregistrés ?
    public boolean aDesEnAttente() {
        return !file.isEmpty();
    }

    /// **Enregistre** tous les déplacements en attente (un appel service par point), puis recharge la
    /// carte. En cas d'échec d'un point, le motif est rapporté et les déplacements **non encore écrits
    /// restent en attente**.
    ///
    /// @return le nombre de points effectivement enregistrés
    public int enregistrer() {
        int enregistres = 0;
        var iterateur = file.entrySet().iterator();
        try {
            while (iterateur.hasNext()) {
                Map.Entry<Long, double[]> entree = iterateur.next();
                serviceSites.deplacerPoint(entree.getKey(), entree.getValue()[0], entree.getValue()[1]);
                iterateur.remove();
                enregistres++;
            }
            rapporterErreur.accept("");
        } catch (RuntimeException echec) {
            rapporterErreur.accept(echec.getMessage());
        }
        modifiees.set(!file.isEmpty());
        rechargerCarte.run();
        return enregistres;
    }

    /// **Abandonne** les déplacements en attente et recharge la carte : les marqueurs reviennent à leurs
    /// positions enregistrées.
    public void annuler() {
        file.clear();
        modifiees.set(false);
        rechargerCarte.run();
    }
}
