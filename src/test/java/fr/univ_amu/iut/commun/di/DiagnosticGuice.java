package fr.univ_amu.iut.commun.di;

import com.google.inject.ConfigurationException;
import com.google.inject.Injector;
import javafx.util.Callback;

/// La fabrique de contrôleurs d'un `FXMLLoader` de test, qui **nomme la cause** quand Guice ne peut
/// pas construire (#4767).
///
/// Une liaison manquante remontait sous cette forme :
///
/// ```
/// java.lang.IllegalArgumentException: Unsupported class file major version 69
/// ```
///
/// Qui ne le sait pas cherche un problème de version de Java. Le message vient du **rendu** du
/// message de Guice, pas de l'erreur : `getMessage()` ne rend que « Guice configuration errors: », et
/// il faut descendre dans la trace pour trouver « No implementation for Horloge was bound ».
/// `getErrorMessages()`, lui, la donne toujours.
public final class DiagnosticGuice {

    private DiagnosticGuice() {}

    /// La fabrique à poser sur `loader.setControllerFactory`, à la place de `injecteur::getInstance`.
    ///
    /// @param injecteur l'injecteur du test
    /// @return la fabrique, qui rend la cause au lieu de la cacher
    public static Callback<Class<?>, Object> pour(Injector injecteur) {
        return type -> {
            try {
                return injecteur.getInstance(type);
            } catch (ConfigurationException echec) {
                throw new AssertionError(
                        "Guice n'a pas pu construire " + type.getSimpleName() + " : " + echec.getErrorMessages(),
                        echec);
            }
        };
    }
}
