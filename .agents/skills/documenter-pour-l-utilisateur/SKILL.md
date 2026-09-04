---
name: documenter-pour-l-utilisateur
description: Use at closure pass 4, once the developer documentation matches the code, to document the chantier for the people who use the product. A visible capability without a capture is half delivered, and a state shown incidentally is nearly as fragile as one shown nowhere.
license: GPL-3.0-or-later
metadata:
  langue: fr
  origine: dev-docs/cycle-de-chantier.md
---

# Documenter pour l'utilisateur

## Loi d'airain

```
UNE FONCTIONNALITÉ VISIBLE SANS CAPTURE EST À MOITIÉ LIVRÉE
```

Le site produit s'adresse à qui se sert de l'application, pas à qui l'écrit. Une capacité décrite en
prose et jamais montrée oblige son lecteur à imaginer l'écran.

## Annoncer

« J'utilise la compétence documenter-pour-l-utilisateur sur ce que <le chantier> rend visible. »

## Ce que la passe fait, et ce qu'elle ne fait pas

Elle écrit dans `docs/`, le site produit, et fait produire les captures qui l'illustrent.

Elle **ne regarde pas** les écrans état par état : c'est la passe 8, qui vient après l'harmonisation
parce que celle-ci casse des écrans sans casser de test. Les deux touchent aux captures et n'ont pas
le même objet : ici on **documente une capacité**, là on **inspecte un écran**.

## Fonction de garde

```
1. NOMMER    les capacites que le chantier rend visibles a l utilisateur.
2. ECRIRE    leur page ou leur section dans `docs/`, dans ses mots a lui.
3. CAPTURER  le cas NOMINAL par la capture principale.
4. DEDIER    une capture a CHAQUE etat particulier, avec sa section.
5. DECLARER  la capture : la classe `Capture*`, `capture_screenshots.py`, le
             `captures.manifest`. Les trois, ou la CI refuse.
6. RELIRE    ce que la page dit a la grille : c est de la prose lue par
             quelqu un qui n a pas suivi.
```

## Le geste concret, en trois déclarations

Une capture ne se produit pas en lançant quelque chose à la main. Elle se **déclare**, à trois
endroits, et la marche à suivre complète vit dans « Ajouter une fonctionnalité §7 » :

- une classe `CaptureMaFeature`, écrite sur le patron existant ;
- son ajout à `capture_screenshots.py`, qui lance chaque `Capture*` dans son propre JVM ;
- l'aperçu déclaré au `captures.manifest`.

Les PNG vivent dans `.github/assets/` et le hook `scripts/mkdocs_hooks.py` les expose sous
`assets/captures/` au build du site.

## L'état montré par accident, et ce qu'il coûte

**La capture principale montre le cas ordinaire ; chaque écart a la sienne.** Le seed de la capture
principale doit produire l'état nominal, sans quoi un état particulier s'installe dans l'image de
référence sans que personne ne l'ait voulu.

Mesuré sur #2222 : la page du Diagnostic s'illustrait d'une nuit **hors nuit**. L'alerte y était
visible sans être ni nommée ni documentée, et un simple ajustement des horaires du seed l'aurait fait
disparaître sans que personne ne le voie.

Un état montré **incidemment** est presque aussi fragile qu'un état montré nulle part.

## Ce que la CI refuse

Trois gardes tournent sur cette passe, et ils refusent plutôt qu'ils n'avertissent :
`check_captures.py` refuse une vue sans aperçu, `check_doc_images.py` une page qui pointe une image
absente, `check_doc_videos.py` la même chose pour les clips.

Le premier lit le **code** et non le disque : une vue qui existe sans capture est un défaut, même si
aucune page ne la cite encore.

## Signaux d'alerte : on s'arrête

| Pensée | Réalité |
|---|---|
| « C'est décrit dans la page, ça suffit » | Une capacité visible sans capture est à moitié livrée |
| « La capture principale montre bien la fonctionnalité » | Montre-t-elle l'état **ordinaire** ? Sinon un cas particulier s'y installe |
| « Cet état se voit sur la capture existante » | Montré incidemment, il disparaît au premier ajustement du seed |
| « J'ai écrit la classe `Capture*` » | Trois déclarations, pas une. Le script et le manifeste aussi |
| « J'ai regardé les écrans, la passe 4 est faite » | Regarder les états est la passe 8. Ici on documente une capacité |
| « La page est claire pour moi » | Elle est lue par quelqu'un qui n'a pas suivi le chantier |
