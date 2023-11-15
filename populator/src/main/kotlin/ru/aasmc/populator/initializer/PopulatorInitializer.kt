package ru.aasmc.populator.initializer

import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.stereotype.Component
import ru.aasmc.populator.service.PopulatorService

@Component
class PopulatorInitializer(
    private val populatorService: PopulatorService
): ApplicationListener<ContextRefreshedEvent> {

    override fun onApplicationEvent(event: ContextRefreshedEvent) {
        populatorService.populate()
    }
}