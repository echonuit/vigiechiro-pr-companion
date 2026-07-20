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

    /// La version à **afficher**, jamais nulle ni vide.
    ///
    /// À préférer pour tout ce qui est montré ou journalisé : « À propos », `--version`, en-tête de
    /// journal, corps d'un signalement d'anomalie.
    public String libelle() {
        return versionEmpaquetee().orElse(INCONNUE);
    }
}
