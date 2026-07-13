package fr.univ_amu.iut.commun.api;

import java.util.Optional;

/// État du **traitement serveur** d'une participation (`participation.traitement.etat`), c'est-à-dire
/// l'analyse Tadarida lancée par `POST /participations/#id/compute` (#1237) et exécutée par la plateforme.
///
/// C'est un état **observé**, pas un statut du workflow local : il appartient au serveur, il n'est pas
/// monotone (une relance ramène `FINI` à `PLANIFIE`) et l'application ne fait que le lire. Il reste donc
/// délibérément **distinct** de [fr.univ_amu.iut.commun.model.StatutWorkflow], dont `DEPOSE` demeure
/// l'état terminal (« ma part locale est faite ») — même choix que `StatutPlateforme` côté sites (EPIC
/// #1259).
///
/// Les cinq valeurs sont celles du backend (`Scille/vigiechiro-api`, `resources/participations.py:73`).
/// Le serveur réessaie **une** fois automatiquement (`TASK_PARTICIPATION_MAX_RETRY=1`) : un premier échec
/// donne [#RETRY], un second [#ERREUR].
public enum EtatTraitement {

    /// Traitement **mis en file d'attente** (`date_planification` posée par le compute), pas encore pris en
    /// charge par un worker. Le passage à [#EN_COURS] dépend de l'ordonnanceur de la plateforme.
    PLANIFIE,

    /// Un worker **exécute** l'analyse (`date_debut` posée). Compter en dizaines de minutes, voire en
    /// heures : le calcul tourne sur une ferme de calcul distante.
    EN_COURS,

    /// Analyse **terminée** (`date_fin` posée) : c'est le **seul** état où `GET /participations/#id/donnees`
    /// renvoie les observations. Avant, l'endpoint répond « 200 liste vide » (et non une erreur).
    FINI,

    /// Analyse **en échec** après épuisement des essais : `traitement.message` porte la trace serveur.
    ERREUR,

    /// Un essai a échoué et le serveur a **relancé automatiquement** l'analyse (`traitement.retry` compte
    /// les essais). Du point de vue de l'utilisateur, le travail est toujours en cours.
    RETRY;

    /// Lecture **tolérante** de la valeur brute du serveur : vide si la chaîne est absente, vide, ou
    /// inconnue de cette énumération. Ne lève jamais — le serveur peut introduire un état sans nous
    /// prévenir, et une participation jamais calculée n'a pas de bloc `traitement` du tout.
    public static Optional<EtatTraitement> depuis(String valeur) {
        if (valeur == null || valeur.isBlank()) {
            return Optional.empty();
        }
        for (EtatTraitement etat : values()) {
            if (etat.name().equalsIgnoreCase(valeur.trim())) {
                return Optional.of(etat);
            }
        }
        return Optional.empty();
    }

    /// Les observations sont-elles récupérables ? Vrai pour [#FINI] seulement : c'est la condition à
    /// laquelle l'import « depuis VigieChiro » peut aboutir.
    public boolean resultatsDisponibles() {
        return this == FINI;
    }

    /// Le serveur travaille-t-il encore (ou s'apprête-t-il à le faire) ? [#PLANIFIE], [#EN_COURS] et
    /// [#RETRY] : dans les trois cas, il n'y a rien à faire d'autre qu'attendre.
    public boolean enAttente() {
        return this == PLANIFIE || this == EN_COURS || this == RETRY;
    }

    /// L'analyse a-t-elle définitivement échoué ? Seul [#ERREUR] l'est : [#RETRY] est un échec **rattrapé**
    /// par le serveur.
    public boolean enEchec() {
        return this == ERREUR;
    }
}
