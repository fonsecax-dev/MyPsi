# O Glance cria as ações do widget por reflexão a partir do nome da classe (actionRunCallback<...>()).
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }
