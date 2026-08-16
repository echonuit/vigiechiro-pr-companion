package fr.univ_amu.iut.commun.model;

import com.google.inject.Inject;
import java.util.Optional;

/// La version de l'application, telle qu'elle a été **empaquetée** (#2108).
///
/// Elle vient de l'entrée `Implementation-Version` du manifeste, inscrite au jar mince par le
/// `maven-jar-plugin` et au fat-jar par le transformer du `maven-shade-plugin` - deux chemins
/// distincts, parce que le shade **reconstruit** son manifeste au lieu d'hériter de celui du jar
/// mince. C'est le fat-jar que jpackage empaquette, donc celui que l'utilisateur exécute.
///
/// **Lancée hors d'un jar** - depuis les classes Maven (`javafx:run`, tests, outils de capture) - il
/// n'y a aucun manifeste à lire. Ce cas n'est pas une erreur : c'est le quotidien du développement.
/// La classe répond alors [#INCONNUE], et [#versionEmpaquetee()] rend un `Optional` vide pour que
/// l'appelant qui a besoin de la **vraie** version puisse distinguer les deux situations. Une
/// vérification de mise à jour, par exemple, n'a aucun sens sans version de référence : mieux vaut
/// qu'elle s'abstienne que de comparer à une valeur inventée.
public final class VersionApplication {

    /// Ce qui s'affiche quand l'application ne tourne pas depuis un jar. Volontairement lisible par
    /// un humain plutôt que vide : dans un « À propos » ou un `--version`, une ligne blanche ne dit
    /// pas si l'information manque ou si le champ est cassé.
    public static final String INCONNUE = "version de développement";

    private final String versionLue;

    /// Construit le service à partir du manifeste de la classe indiquée.
    ///
    /// La classe de référence est un paramètre, et non `VersionApplication.class` en dur, pour que
    /// les tests puissent exercer les deux branches sans dépendre de la façon dont la suite est
    /// lancée - le manifeste est une propriété de l'**empaquetage**, pas du code.
    public VersionApplication(Class<?> classeDeReference) {
        Package paquet = classeDeReference.getPackage();
        this.versionLue = paquet == null ? null : paquet.getImplementationVersion();
    }

    /// Service tel qu'injecté en production : la version lue depuis le paquet de l'application.
    ///
    /// L'annotation est nécessaire, et son absence ne se voit pas à la compilation : la classe expose
    /// **deux** constructeurs publics (celui-ci et celui qui prend une classe de référence, pour les
    /// tests), et Guice refuse alors de choisir. L'échec ne survient qu'à la construction de
    /// l'injecteur, donc à l'ouverture d'un écran.
    @Inject
    public VersionApplication() {
        this(VersionApplication.class);
    }

    /// La version empaquetée, ou un `Optional` vide hors d'un jar.
    ///
    /// À préférer dès qu'une décision en dépend (comparer, publier, signaler) : un `Optional` vide
    /// force l'appelant à traiter l'absence, là où [#libelle()] la masquerait derrière une phrase.
    public Optional<String> versionEmpaquetee() {
        return Optional.ofNullable(versionLue).filter(v -> !v.isBlank());
    }

    /// Un service qui rend `version` telle quelle, **sans lire aucun manifeste** (#3876).
    ///
    /// ## Pourquoi une fabrique, et pourquoi pas un constructeur
    ///
    /// Un troisième constructeur public rendrait le choix de Guice encore plus ambigu qu'il ne l'est
    /// déjà - la classe en expose deux, et c'est pour cela que le sien porte `@Inject`. Une fabrique
    /// statique ne participe pas à l'injection : elle ne peut donc pas créer ce piège.
    ///
    /// ## À quoi elle sert, et à quoi elle ne doit pas servir
    ///
    /// Aux **outils de capture**, et à eux seuls. Hors d'un jar - `javafx:run`, tests, capture -
    /// [#versionEmpaquetee()] rend un `Optional` vide, si bien que rien qui **compare** des versions ne
    /// peut s'exercer. L'aperçu de l'annonce de mise à jour en dépendait : sans version locale, le
    /// vérificateur ne propose rien et le bandeau ne s'affiche pas. Mesuré en rendant un aperçu vide.
    ///
    /// ⚠️ **Ce n'est pas un moyen de forcer la version affichée en production.** Ce que l'application
    /// dit d'elle-même doit venir de son empaquetage, sans quoi un « À propos » pourrait mentir.
    ///
    /// @param version la version à rendre, telle qu'un manifeste l'écrirait (ex. `2.21.3`)
    public static VersionApplication figeeA(String version) {
        return new VersionApplication(version);
    }

    /// Constructeur de la fabrique ci-dessus, **privé** : c'est ce qui le tient hors de portée de
    /// Guice, et donc hors du piège d'ambiguïté que documente le constructeur annoté.
    private VersionApplication(String version) {
        this.versionLue = version;
    }

    /// La version à **afficher**, jamais nulle ni vide.
    ///
    /// À préférer pour tout ce qui est montré ou journalisé : « À propos », `--version`, en-tête de
    /// journal, corps d'un signalement d'anomalie.
    public String libelle() {
        return versionEmpaquetee().orElse(INCONNUE);
    }
}
