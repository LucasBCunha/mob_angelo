package br.ufpr.nr2.mobangelo

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.*
import android.widget.AdapterView.OnItemSelectedListener
import androidx.appcompat.app.AppCompatActivity
import br.ufpr.nr2.mobangelo.bluetooth.Constants


class ConfigurationActivity : AppCompatActivity(), View.OnClickListener {

    private var spin: Spinner? = null
    var spinVal: String? = null
    var competencies = arrayOf("Outra", "Médico(a)", "Enfermeiro(a)", "Policial") //array of strings used to populate the spinner

    var edtEmergency : EditText? = null
    private val checkBoxes : ArrayList<CheckBox> = arrayListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_configuration)

        spin = findViewById<View>(R.id.sp_competency) as Spinner //fetching view's id

        //Register a callback to be invoked when an item in this AdapterView has been selected
        //Register a callback to be invoked when an item in this AdapterView has been selected
        spin?.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(
                arg0: AdapterView<*>?, arg1: View?,
                position: Int, id: Long
            ) {
                spinVal = competencies[position] //saving the value selected
            }

            override fun onNothingSelected(arg0: AdapterView<*>?) {
                // Do nothing
            }
        }
        //setting array adaptors to spinners
        //ArrayAdapter is a BaseAdapter that is backed by an array of arbitrary objects
        val spinAdapter: ArrayAdapter<String> =
            ArrayAdapter(this, R.layout.support_simple_spinner_dropdown_item, competencies)

        // setting adapters to spinners
        spin?.adapter = spinAdapter

        findViewById<Button>(R.id.bt_save_config).setOnClickListener(this)
        edtEmergency = findViewById(R.id.edt_emergency_msg)

        checkBoxes.add(findViewById(R.id.cb_health))
        checkBoxes.add(findViewById(R.id.cb_movies))
        checkBoxes.add(findViewById(R.id.cb_sports))
        supportActionBar?.setDisplayShowHomeEnabled(true);
        supportActionBar?.setLogo(R.drawable.ic_mobangelo_white);
        supportActionBar?.setDisplayUseLogoEnabled(true);
    }

    override fun onResume() {
        super.onResume()

        loadConfiguration()
    }

    override fun onClick(v: View?) {
        when(v?.id){
            R.id.bt_save_config -> {
                if(validateConfigurations()){
                    Toast.makeText(this, "Configuração salva", Toast.LENGTH_SHORT).show()
                    saveConfigurations()
                } else {
                    Toast.makeText(this, "Por favor preencha todos os campos", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun validateConfigurations() : Boolean{
        return edtEmergency?.text?.length!! > 0 && checkBoxes.any { it.isChecked }
    }

    @SuppressLint("ApplySharedPref")
    private fun saveConfigurations(){
        val editor = getSharedPreferences(MainActivity.CONFIG_FILE, 0).edit()
        editor.putInt(Constants.CONFIG_COMPETENCY, spin!!.selectedItemPosition)
        editor.putString(Constants.CONFIG_INTERESTS, convertToString())
        editor.putString(Constants.CONFIG_MESSAGE, edtEmergency?.text.toString())
        editor.commit()
    }

    private fun loadConfiguration(){
        loadCompetence()
        loadInterests()
        loadMessage()
    }

    private fun loadInterests() {
        val interestsList = getInterestsValue()
        if (interestsList != null){
            convertFromString(interestsList)
        }
    }

    private fun getInterestsValue(): String? {
        val sp = getSharedPreferences(MainActivity.CONFIG_FILE, 0)
        return sp.getString(Constants.CONFIG_INTERESTS, null)
    }

    private fun loadMessage() {
        val sp = getSharedPreferences(MainActivity.CONFIG_FILE, 0)
        val standardText = "Por favor ligue para (41) " +
                "99999-9999 para pedir ajuda e avise que tenho diabetes"
        edtEmergency?.setText(sp.getString(Constants.CONFIG_MESSAGE, standardText))
    }

    private fun loadCompetence(){
        val tmp = getCompetenceValue()
        if(tmp < 0){
            return
        }
        spin?.setSelection(tmp)
        return
    }

    private fun getCompetenceValue(): Int {
        val sp = getSharedPreferences(MainActivity.CONFIG_FILE, 0)
        return sp.getInt(Constants.CONFIG_COMPETENCY, -1)
    }

    private fun convertFromString(s : String){
        findViewById<CheckBox>(R.id.cb_health).isChecked = s[0] == '1'
        findViewById<CheckBox>(R.id.cb_movies).isChecked = s[1] == '1'
        findViewById<CheckBox>(R.id.cb_sports).isChecked = s[2] == '1'
    }

    private fun convertToString() : String{
        var string = ""
        string += if(findViewById<CheckBox>(R.id.cb_health).isChecked){
            "1"
        } else{
            "0"
        }

        string += if(findViewById<CheckBox>(R.id.cb_movies).isChecked){
            "1"
        } else{
            "0"
        }

        string += if(findViewById<CheckBox>(R.id.cb_sports).isChecked){
            "1"
        } else{
            "0"
        }

        return string
    }

}
