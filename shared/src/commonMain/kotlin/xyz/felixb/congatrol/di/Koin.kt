package xyz.felixb.congatrol.di

import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.koin.mp.KoinPlatform
import xyz.felixb.congatrol.ActionViewModel
import xyz.felixb.congatrol.data.HttpRobotApi
import xyz.felixb.congatrol.data.LicenseRepository
import xyz.felixb.congatrol.data.RobotApi
import xyz.felixb.congatrol.data.RobotRepository
import xyz.felixb.congatrol.screens.drive.DriveViewModel
import xyz.felixb.congatrol.screens.licenses.LicenseDetailViewModel
import xyz.felixb.congatrol.screens.licenses.LicensesViewModel
import xyz.felixb.congatrol.screens.map.MapViewModel
import xyz.felixb.congatrol.screens.robot.RobotViewModel

val appModule =
    module {
        single<RobotApi> { HttpRobotApi() }
        single { RobotRepository(get(), get()) }
        viewModelOf(::ActionViewModel)
        viewModelOf(::MapViewModel)
        viewModelOf(::DriveViewModel)
        viewModelOf(::RobotViewModel)
        single { LicenseRepository() }
        viewModelOf(::LicensesViewModel)
        viewModelOf(::LicenseDetailViewModel)
    }

/**
 * Starts the graph. Each platform passes a module binding what only it can build: the
 * `SettingsStore`, which keeps the robot's address and name between launches.
 */
fun initKoin(platformModule: Module) {
    startKoin {
        modules(platformModule, appModule)
    }
}

/**
 * Has the app talk to an invented robot instead of a real one — for screenshots and for looking
 * at the app without a robot, launched with a `demo` flag. Must be called before the first screen
 * reads.
 */
fun requestDemoRobot() {
    KoinPlatform.getKoin().get<RobotRepository>().enterDemo()
}
