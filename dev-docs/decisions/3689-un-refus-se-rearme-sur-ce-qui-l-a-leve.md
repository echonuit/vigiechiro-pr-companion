# ADR 3689 - Un refus définitif se réarme sur ce qui a levé sa cause, et sur rien d'autre

- **Statut** : Accepté - 2026-08-18
- **Chantier** : #3689, dernière suite de l'EPIC #3900
- **Vérification** : certaine - `DepotUniteDaoTest#le_rearmement_ne_touche_que_les_refus_d_authentification`

Vue rouge en retirant le filtre sur la cause : le réarmement touche alors **deux** unités au lieu d'une,
et ramène un contenu refusé que rien n'a réparé.

## Contexte

Depuis #3469, une unité de dépôt refusée par le serveur porte `echec_definitif`. Depuis #3687, l'écran
cesse de proposer une reprise sur ces unités : le bouton ne promet plus ce qu'il ne peut pas tenir.

Mais **rien ne les réarme**. Seule une tentative réussie effaçait le drapeau - or c'est précisément la
tentative qu'on n'offre plus. Une nuit dont toutes les archives ont été refusées reste donc coincée,
sans recours et sans que rien ne le dise.

⚠️ L'issue avait anticipé ce moment : « c'est acceptable aujourd'hui, parce que la reprise est encore
offerte ; ça ne le sera plus le jour où elle cessera de l'être ». #3687 est ce jour-là.

## Les trois pistes écartées

Chacune réarme **à côté** de la cause :

| Piste | Pourquoi non |
|---|---|
| au prochain lancement | un jeton se renouvelle **à la connexion**, pas au démarrage : on réarmerait avant que quoi que ce soit ait changé |
| sur un geste « forcer la reprise » | c'est le bouton qui promet ce qu'il ne peut pas tenir, sous un autre nom |
| après un délai | un droit refusé ne se répare pas tout seul |

## La décision

**Une unité refusée définitivement se réarme sur l'événement qui pouvait plausiblement lever sa cause.**

Ce qui suppose de connaître la cause, et ces refus ne sont pas de même nature :

- **401 / 403** - authentification, droits S3, URL signée expirée. Une **reconnexion réussie** peut les
  lever ;
- **400 / 422** et autres 4xx - le contenu lui-même est refusé. Aucun événement extérieur ne le répare.

Le bouton ne réapparaît donc que quand **quelque chose a réellement changé**.

## Ce que la décision a coûté, et pourquoi c'était le prix juste

Le statut existait déjà, mais **seulement dans le texte** de `message_erreur` (« HTTP 403 : … »). Or le
dépôt s'interdit de le relire, et la migration V39 le rappelle :

> jamais d'une lecture du texte de la raison : la même panne s'y écrit de trop de façons pour qu'on la
> redevine.

Distinguer les deux familles demandait donc une **colonne** (`cause_refus`, migration V41), décidée à
l'émission depuis le statut HTTP. C'est une migration et un champ de plus ; c'était l'arbitrage, et il a
été pris explicitement plutôt que contourné par une lecture de chaîne.

⚠️ Une **colonne** et non un statut, pour la raison que V39 développe déjà : `restantes()` rend « tout
sauf déposé », et `toutesDeposees()` vaut « `restantes()` est vide ». Un statut ferait basculer le
passage en DÉPOSÉ alors qu'il manque des sons.

## Conséquences

`connexion` ne connaît pas la table des unités : le geste passe par un **port** `RearmementDepot`
déclaré à vide dans `CommunModule` et implémenté par `lot`, comme `PointsDuCarre` et
`ImportObservations`. Sans la feature `lot`, il n'y a rien à réarmer, et ne rien faire est le
comportement juste.

Le **message d'erreur survit** au réarmement : il dit ce qui s'est passé la fois d'avant, et c'est
encore vrai. La prochaine tentative l'écrasera.

**Ce qui n'est pas couvert** : un contenu refusé (400 / 422) ne se réarme jamais automatiquement. Le
geste qui le lèverait est la **régénération de l'archive**, et il n'est pas câblé ici - une issue de
suite le fera si le besoin se présente. Réarmer sur autre chose ramènerait le bouton que #3687 vient
de faire taire.
