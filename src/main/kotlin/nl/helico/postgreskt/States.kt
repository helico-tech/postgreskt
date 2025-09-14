package nl.helico.postgreskt

sealed interface State

data object Disconnected : State

data object Connecting : State
