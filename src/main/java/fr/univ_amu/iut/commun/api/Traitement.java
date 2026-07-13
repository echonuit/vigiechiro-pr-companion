package fr.univ_amu.iut.commun.api;

import java.util.Optional;

/// Bloc `traitement` d'une participation : l'**analyse Tadarida côté serveur**, telle que la plateforme la
/// rapporte (`GET /participations/#id`). Photographie à l'instant de la lecture, jamais une vérité durable
/// — le serveur peut être en train de travailler pendant qu'on la regarde (EPIC #1259).
///
/// Une participation **jamais calculée** n'a pas de bloc `traitement` du tout : c'est [#absent()], que le
/// parseur substitue au `null` pour épargner aux appelants un test de nullité de plus.
///
/// ⚠️ **Une relance n'est pas anodine.** Le serveur remplace tout le bloc `traitement` au `compute`, et
/// surtout il **supprime toutes les `donnees` existantes** avant de recalculer
/// (`task_participation.py:726-731`). Sur une nuit déposée en **archives ZIP** — notre mode par défaut
/// depuis #984 — les WAV extraits ne sont pas conservés sur S3 : le recalcul ne peut donc pas les relire,
/// et les observations sont **définitivement perdues** (#1244). Un premier lancement est sûr ; une relance
/// ne l'est pas.
///
/// ⚠️ **Le serveur remplace ce bloc, il ne le complète pas** : à chaque étape il réécrit le sous-document
/// entier. Une fois le calcul démarré, le bloc devient `{etat, date_debut}` et la **date de planification
/// disparaît** ; à la fin, `date_fin` s'ajoute. N'attendez donc jamais les trois dates à la fois — vérifié
/// en réel sur la participation canonique (`FINI`, sans `date_planification`).
///
/// @param etat état courant, ou `null` si le serveur n'a pas de traitement pour cette participation (ou
///     s'il en rapporte un que nous ne connaissons pas : lecture tolérante, cf. [EtatTraitement#depuis])
/// @param datePlanification mise en file d'attente (`date_planification`), ISO 8601, ou `null` — en
///     pratique présente au seul état [EtatTraitement#PLANIFIE], le serveur l'écrasant ensuite
/// @param dateDebut prise en charge par un worker (`date_debut`), ISO 8601, ou `null`
/// @param dateFin fin de l'analyse (`date_fin`), ISO 8601, ou `null` ; posée aussi sur échec
/// @param message trace d'erreur du serveur (`message`), renseignée sur [EtatTraitement#ERREUR] et
///     [EtatTraitement#RETRY], `null` sinon
/// @param retry nombre d'essais déjà consommés (`retry`), ou `null` si le serveur ne le rapporte pas
public record Traitement(
        EtatTraitement etat,
        String datePlanification,
        String dateDebut,
        String dateFin,
        String message,
        Integer retry) {

    /// Aucun traitement connu : participation jamais calculée, ou bloc `traitement` illisible.
    private static final Traitement ABSENT = new Traitement(null, null, null, null, null, null);

    /// Le « rien » du traitement, substitué au `null` par le parseur.
    public static Traitement absent() {
        return ABSENT;
    }

    /// Le serveur n'a-t-il rien à dire sur cette participation ? Vrai tant qu'aucun compute n'a été lancé
    /// (ou que l'état rapporté nous est inconnu).
    public boolean estInconnu() {
        return etat == null;
    }

    /// **Le motif d'un échec, en une ligne.** Le serveur renvoie une pile Python entière : la donner telle
    /// quelle à l'observateur ne l'aide pas, la masquer le laisse sans prise pour demander de l'aide. On en
    /// garde donc la **première ligne**, celle qui nomme l'erreur.
    ///
    /// Vide si le serveur n'a rien à dire (états sains, ou trace absente). Rendue ici plutôt que recopiée
    /// par chaque écran : la carte de M-Lot, la ligne de commande et le diagnostic d'import posaient la
    /// même question à trois endroits (harmonisation, clôture de l'EPIC #1259).
    public Optional<String> motifCourt() {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }
        return message.strip().lines().findFirst().filter(ligne -> !ligne.isBlank());
    }

    /// Les observations sont-elles récupérables ? Faux tant que l'état est inconnu.
    public boolean resultatsDisponibles() {
        return etat != null && etat.resultatsDisponibles();
    }

    /// Le serveur travaille-t-il encore ? Faux tant que l'état est inconnu.
    public boolean enAttente() {
        return etat != null && etat.enAttente();
    }

    /// L'analyse a-t-elle définitivement échoué ? Faux tant que l'état est inconnu.
    public boolean enEchec() {
        return etat != null && etat.enEchec();
    }
}
