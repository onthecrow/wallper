package com.onthecrow.wallper.core.viewmodel

abstract class BaseState<Action> {

    open val actions: List<Action> = emptyList()

    /**
     * You should override this method if you want to use actions in your business logic
     */
    open fun updateActions(newActions: List<Action>): BaseState<Action> {
        throw NotImplementedError("Actions not implemented")
    }
}