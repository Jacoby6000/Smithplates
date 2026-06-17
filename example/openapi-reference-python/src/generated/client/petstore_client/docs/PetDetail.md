# PetDetail

Aggregate read model returned by GetPet; mirrors joined repository output.

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **str** |  | 
**name** | **str** |  | 
**status** | [**PetStatus**](PetStatus.md) |  | 
**species** | [**PetSpecies**](PetSpecies.md) |  | 
**category_id** | **str** |  | 
**owner_id** | **str** |  | [optional] 
**tag_count** | **float** |  | 
**tags** | **List[str]** |  | 
**attributes** | [**List[PetAttribute]**](PetAttribute.md) |  | 
**photo** | **bytearray** |  | [optional] 
**metadata** | **object** |  | [optional] 
**adopted_at** | **datetime** |  | [optional] 
**created_at** | **datetime** |  | 
**updated_at** | **datetime** |  | 
**category** | [**CategorySummary**](CategorySummary.md) |  | 
**store** | [**StoreSummary**](StoreSummary.md) |  | 
**owner** | [**OwnerSummary**](OwnerSummary.md) |  | [optional] 
**profile** | [**PetProfileSummary**](PetProfileSummary.md) |  | [optional] 

## Example

```python
from petstore_client.models.pet_detail import PetDetail

# TODO update the JSON string below
json = "{}"
# create an instance of PetDetail from a JSON string
pet_detail_instance = PetDetail.from_json(json)
# print the JSON string representation of the object
print(PetDetail.to_json())

# convert the object into a dict
pet_detail_dict = pet_detail_instance.to_dict()
# create an instance of PetDetail from a dict
pet_detail_from_dict = PetDetail.from_dict(pet_detail_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


