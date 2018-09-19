package io.particle.mesh.setup.ui

import android.os.Bundle
import android.support.v4.app.Fragment


open class BaseMeshSetupFragment : Fragment() {

    protected lateinit var flowManagerVM: FlowManagerAccessModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        flowManagerVM = FlowManagerAccessModel.getViewModel(this)
    }

}