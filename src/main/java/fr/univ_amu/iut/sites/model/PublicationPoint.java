package fr.univ_amu.iut.sites.model;

import com.google.gson.JsonArray;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.FournisseurToken;
import fr.univ_amu.iut.commun.api.LocalitesDuSite;
import fr.univ_amu.iut.commun.api.PointVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.model.DistanceGeo;
import fr.univ_amu.iut.sites.model.dao.PointPublieDao;
import java.util.Objects;
import java.util.Optional;

/// Publie un point d'écoute **sur la plateforme** (#3458), sans effacer ceux des autres.
///
/// ## Pourquoi cette classe existe
///
/// `PUT /sites/{id}/localites` **remplace la liste entière** (`{'$set': {'localites': ...}}`). Ajouter un
/// point suppose donc de renvoyer tous les autres - souvent ceux d'un observateur qui n'est pas nous :
/// le carré d'essai en porte quarante et un, et son propriétaire est quelqu'un d'autre.
///
/// ⚠️ **Et le serveur ne protège pas cette écriture.** `set_localite` appelle `sites.update(...)` **sans
/// `if_match`**, là où `taxons.py` et `protocoles.py` en posent un ; le socle du backend le commente
/// lui-même : *« No if_match, in case of race condition, repeatedly try the update »*. Une modification
/// concurrente est donc écrasée **sans erreur et sans trace**. Le client officiel, lui, n'envoie aucun
/// `_etag` : il écrase en aveugle.
///
/// ## La garde, et ce qu'elle ne peut pas
///
/// On lit, on construit l'union, puis on **relit juste avant d'écrire** : si l'`_etag` du site a bougé
/// entre les deux, on renonce. La fenêtre tombe sous la seconde.
///
/// ⚠️ **Elle ne se ferme pas.** Sur une collision exacte - une écriture concurrente entre notre relecture
/// et notre envoi - on écrase sans le savoir. Seul le serveur pourrait l'empêcher, et il ne le fait pas.
/// C'est un risque **assumé**, pas un oubli : le dire ici vaut mieux que laisser croire à une garantie.
public class PublicationPoint {

    private final ClientVigieChiro client;
    private final PointPublieDao publies;
    private final FournisseurToken token;

    public PublicationPoint(ClientVigieChiro client, PointPublieDao publies, FournisseurToken token) {
        this.client = Objects.requireNonNull(client, "client");
        this.publies = Objects.requireNonNull(publies, "publies");
        this.token = Objects.requireNonNull(token, "token");
    }

    /// Un jeton est-il enregistré ? La **seule** condition d'échec qui se sache d'avance.
    ///
    /// ⚠️ Ne pas chercher à en déduire davantage. Le refus d'écriture (403) dépend de choses que
    /// Companion ne connaît pas : le propriétaire du carré, et la validation de l'observateur sur son
    /// protocole. Les liens de site viennent de `GET /moi/participations` et non de `/moi/sites` (#718,
    /// cf. [ClientVigieChiro#mesSites()]), donc un carré relié peut appartenir à quelqu'un d'autre.
    /// Prédire le refus à partir du verrouillage bloquerait le participant validé, à qui la plateforme
    /// dit oui.
    public boolean connecte() {
        return token.token().isPresent();
    }

    /// Ce qu'une publication peut donner.
    public sealed interface Resultat {

        /// Le point est en ligne.
        record Publie() implements Resultat {}

        /// Une localité portait déjà ce nom **au même endroit** : rien n'a été envoyé, et il n'y avait
        /// rien à envoyer. La plateforme impose l'unicité des noms.
        record DejaPresent(String nom) implements Resultat {}

        /// Une localité porte ce nom, mais **ailleurs** : rien n'a été envoyé, et rien n'est retenu.
        ///
        /// C'est le cas qu'il ne faut surtout pas confondre avec le précédent. Une participation
        /// **nomme** sa localité, donc écraser la position distante déplacerait d'un coup toutes les
        /// nuits qui s'y rattachent, y compris celles d'autres observateurs. Et la marquer « publiée »
        /// sans rien envoyer serait pire encore : l'écran annoncerait en ligne un point qui, à cet
        /// endroit, ne l'est pas.
        ///
        /// `distanceMetres` vaut `NaN` quand la position distante est **illisible** : on sait qu'un
        /// homonyme existe, pas où il est.
        record AilleursSurLaPlateforme(String nom, double distanceMetres) implements Resultat {}

        /// Le site a changé entre la lecture et l'envoi : rien n'a été envoyé, pour ne pas écraser ce
        /// qu'on n'a pas lu.
        record ModifieEntreTemps() implements Resultat {}

        /// La plateforme a refusé, ou n'a pas répondu. `geste` dit **quoi faire**, pas seulement ce qui
        /// s'est passé (ADR 2635).
        record Refuse(String cause, String geste) implements Resultat {}
    }

    /// Publie `point` sur le site distant `idSite`, et **retient** qu'il y est.
    ///
    /// ⚠️ `idPointLocal` sert à cette mémoire, et à rien d'autre : sans elle, l'écran reproposerait le
    /// geste indéfiniment. Le marquage suit la réussite - et **aussi** le cas « déjà présent », qui
    /// constate la même vérité : le point est en ligne.
    ///
    /// ⚠️ [Resultat.AilleursSurLaPlateforme] n'est **pas** marqué, et c'est tout l'objet de sa
    /// distinction : un homonyme posé ailleurs n'est pas notre point en ligne. Le marquer figerait la
    /// confusion, puisque le geste ne serait plus jamais reproposé.
    public Resultat publier(String idSite, PointVigieChiro point, long idPointLocal) {
        Resultat resultat = envoyer(idSite, point);
        if (resultat instanceof Resultat.Publie || resultat instanceof Resultat.DejaPresent) {
            publies.marquer(idPointLocal);
        }
        return resultat;
    }

    private Resultat envoyer(String idSite, PointVigieChiro point) {
        Objects.requireNonNull(idSite, "idSite");
        Objects.requireNonNull(point, "point");

        ReponseApi<LocalitesDuSite> lecture = client.localitesDuSite(idSite);
        if (!(lecture instanceof ReponseApi.Succes<LocalitesDuSite>(LocalitesDuSite avant))) {
            return refus(lecture, "lire les localités du site");
        }
        if (avant.contient(point.code())) {
            return verdictSurHomonyme(avant, point);
        }

        JsonArray union = avant.avecEnPlus(point);

        // La relecture est le coeur de la garde : entre la première lecture et maintenant, le site a pu
        // changer. Écrire l'union d'un état périmé effacerait ce qui s'y est ajouté depuis.
        ReponseApi<LocalitesDuSite> relecture = client.localitesDuSite(idSite);
        if (!(relecture instanceof ReponseApi.Succes<LocalitesDuSite>(LocalitesDuSite juste))) {
            return refus(relecture, "relire les localités avant d'écrire");
        }
        if (!juste.etag().equals(avant.etag())) {
            return new Resultat.ModifieEntreTemps();
        }

        ReponseApi<String> envoi = client.remplacerLocalites(idSite, union);
        return envoi instanceof ReponseApi.Succes<String> ? new Resultat.Publie() : refus(envoi, "publier le point");
    }

    /// Une localité porte déjà ce nom : est-ce **le nôtre**, ou un autre point qui s'appelle pareil ?
    ///
    /// Position distante illisible - géométrie absente ou malformée : on ne peut pas conclure, et l'on
    /// choisit alors le verdict **prudent**. Rendre `DejaPresent` marquerait le point publié sur la foi
    /// de son seul nom, ce qui est exactement le raccourci que cette méthode existe pour retirer.
    private static Resultat verdictSurHomonyme(LocalitesDuSite avant, PointVigieChiro point) {
        Optional<PointVigieChiro> distante = avant.localite(point.code());
        if (distante.isEmpty()) {
            return new Resultat.AilleursSurLaPlateforme(point.code(), Double.NaN);
        }
        PointVigieChiro la = distante.get();
        double ecart = DistanceGeo.metresEntre(point.latitude(), point.longitude(), la.latitude(), la.longitude());
        // Seuil **partagé avec l'audit en ligne** (#3750) : les deux répondent à « est-ce le même
        // endroit ? », et deux valeurs différentes se contrediraient sous les yeux de l'utilisateur.
        return ecart <= DistanceGeo.ECART_MEME_ENDROIT_METRES
                ? new Resultat.DejaPresent(point.code())
                : new Resultat.AilleursSurLaPlateforme(point.code(), ecart);
    }

    /// Traduit un échec d'API en refus qui **dit quoi faire**.
    ///
    /// Le 403 est le cas nommé, et il a **deux causes** que `set_localite` traite dans la même branche :
    ///
    /// | Cas | Écriture des localités |
    /// |---|---|
    /// | Propriétaire, carré **non verrouillé** | autorisée |
    /// | Propriétaire, carré **verrouillé** | **403** |
    /// | Non-propriétaire **validé** sur le protocole | autorisée, même verrouillé |
    /// | Non-propriétaire non validé | **403** |
    ///
    /// ⚠️ La première version de ce message ne nommait que la seconde cause (« ce carré ne vous appartient
    /// pas »). Pour le cas le plus courant - **son propre carré, verrouillé** - il était faux, et il
    /// envoyait vérifier une inscription qui n'y était pour rien. Les deux causes sont donc nommées, la
    /// plus probable d'abord.
    private static Resultat refus(ReponseApi<?> reponse, String pendant) {
        if (reponse instanceof ReponseApi.Refuse<?> refuse && refuse.statut() == 403) {
            return new Resultat.Refuse(
                    "La plateforme refuse d'écrire les points de ce carré.",
                    "Deux causes possibles. Si ce carré est le vôtre, il est sans doute déjà"
                            + " verrouillé : un carré verrouillé est figé, et seul un administrateur"
                            + " Vigie-Chiro peut le rouvrir. S'il appartient à quelqu'un d'autre, il faut"
                            + " être inscrit et validé sur son protocole : vérifiez votre inscription sur"
                            + " le portail, ou demandez au propriétaire d'ajouter le point.");
        }
        return new Resultat.Refuse(
                "Impossible de " + pendant + ".",
                "Vérifiez votre connexion à Vigie-Chiro, puis réessayez. Rien n'a été modifié sur la" + " plateforme.");
    }
}
