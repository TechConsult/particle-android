package io.particle.particlemesh.meshsetup.ui


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import io.particle.sdk.app.R
import kotlinx.android.synthetic.main.fragment_enter_network_password.view.*


class EnterNetworkPasswordFragment : BaseMeshSetupFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.fragment_enter_network_password, container, false)
        root.action_next.setOnClickListener { onPasswordEntered() }
        return root
    }

    private fun onPasswordEntered() {
        val password = view!!.deviceNameInputLayout.editText!!.text.toString()
        setupController.updateOtherParams(setupController.otherParams.value!!.copy(
                commissionerCredential = password
        ))
        findNavController().navigate(
                R.id.action_enterNetworkPasswordFragment_to_joiningMeshNetworkProgressFragment
        )
    }
}
